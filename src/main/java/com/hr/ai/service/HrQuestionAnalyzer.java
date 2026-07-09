package com.hr.ai.service;

import com.hr.ai.config.IntentRouterProperties;
import com.hr.ai.config.LlmProperties;
import com.hr.ai.config.PresetQueryProperties;
import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.dto.IntentClassification;
import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.intent.LlmIntentAnalyzer;
import com.hr.ai.service.intent.RuleBasedIntentAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * HR 问答前置路由器：优先使用大模型识别意图与槽位，失败或未配置 API Key 时回退规则引擎。
 */
@Slf4j
@Component
public class HrQuestionAnalyzer {

    private static final String CURRENT_QUARTER = "2026-Q1";

    private static final ThreadLocal<CachedAnalysis> REQUEST_CACHE = new ThreadLocal<>();

    private final LlmIntentAnalyzer llmIntentAnalyzer;
    private final RuleBasedIntentAnalyzer ruleBasedIntentAnalyzer;
    private final IntentRouterProperties intentRouterProperties;
    private final LlmProperties llmProperties;
    private final TextToSqlProperties textToSqlProperties;
    private final PresetQueryProperties presetQueryProperties;

    public HrQuestionAnalyzer(LlmIntentAnalyzer llmIntentAnalyzer,
                              RuleBasedIntentAnalyzer ruleBasedIntentAnalyzer,
                              IntentRouterProperties intentRouterProperties,
                              LlmProperties llmProperties,
                              TextToSqlProperties textToSqlProperties,
                              PresetQueryProperties presetQueryProperties) {
        this.llmIntentAnalyzer = llmIntentAnalyzer;
        this.ruleBasedIntentAnalyzer = ruleBasedIntentAnalyzer;
        this.intentRouterProperties = intentRouterProperties;
        this.llmProperties = llmProperties;
        this.textToSqlProperties = textToSqlProperties;
        this.presetQueryProperties = presetQueryProperties;
    }

    public HrQueryIntent analyze(String question, UserPrincipal user) {
        return resolveAnalysis(question, user).getIntent();
    }

    public Optional<NamedEmployeeQuery> extractNamedEmployeeQuery(String question, UserPrincipal user) {
        return resolveAnalysis(question, user).toNamedEmployeeQuery();
    }

    /** 无用户上下文时仅规则提取（Mock SQL 测试等场景） */
    public Optional<NamedEmployeeQuery> extractNamedEmployeeQuery(String question) {
        return ruleBasedIntentAnalyzer.extractNamedEmployeeQuery(question);
    }

    public Optional<String> extractEmployeeNameFromSalaryQuery(String question) {
        return extractNamedEmployeeQuery(question)
                .filter(nq -> nq.getTopic() == com.hr.ai.model.enums.EmployeeQueryTopic.SALARY)
                .map(NamedEmployeeQuery::getEmployeeName);
    }

    public String currentQuarter() {
        return CURRENT_QUARTER;
    }

    public void clearRequestCache() {
        REQUEST_CACHE.remove();
    }

    public IntentClassification resolveAnalysis(String question, UserPrincipal user) {
        CachedAnalysis cached = REQUEST_CACHE.get();
        if (cached != null && cached.matches(question, user.getId())) {
            return cached.classification();
        }

        IntentClassification raw = classifyRaw(question, user);
        IntentClassification routed = applyRoutingPolicy(raw, question, user);
        REQUEST_CACHE.set(new CachedAnalysis(question, user.getId(), routed));
        return routed;
    }

    private IntentClassification classifyRaw(String question, UserPrincipal user) {
        if (intentRouterProperties.isLlmMode() && !llmProperties.isMockMode()) {
            try {
                return llmIntentAnalyzer.classify(question, user);
            } catch (Exception e) {
                log.warn("LLM 意图识别失败，question='{}': {}", question, e.getMessage());
                if (!intentRouterProperties.isFallbackToRule()) {
                    return IntentClassification.builder()
                            .intent(HrQueryIntent.KNOWLEDGE)
                            .source("llm-fallback-knowledge")
                            .build();
                }
            }
        }
        return ruleBasedIntentAnalyzer.classify(question, user);
    }

    private IntentClassification applyRoutingPolicy(IntentClassification raw, String question, UserPrincipal user) {
        String q = question.toLowerCase(Locale.ROOT);
        HrQueryIntent intent = raw.getIntent() != null ? raw.getIntent() : HrQueryIntent.KNOWLEDGE;

        if (intent == HrQueryIntent.KNOWLEDGE) {
            return raw;
        }

        if (textToSqlProperties.isEnabled() && shouldForceTextToSql(q, user, intent)) {
            return copyWithIntent(raw, HrQueryIntent.TEXT_TO_SQL);
        }

        if (presetQueryProperties.isEnabled()) {
            return raw;
        }

        return applyPresetDisabledPolicy(raw, q, question, user);
    }

    private IntentClassification applyPresetDisabledPolicy(IntentClassification raw, String q,
                                                             String question, UserPrincipal user) {
        HrQueryIntent intent = raw.getIntent();

        if (intent == HrQueryIntent.NAMED_EMPLOYEE || raw.toNamedEmployeeQuery().isPresent()) {
            return ensureNamedEmployee(raw);
        }

        if (intent == HrQueryIntent.KNOWLEDGE) {
            return raw;
        }

        if (textToSqlProperties.isEnabled() && canFallbackToTextToSql(q, user)) {
            return copyWithIntent(raw, HrQueryIntent.TEXT_TO_SQL);
        }

        if (raw.toNamedEmployeeQuery().isPresent()) {
            return ensureNamedEmployee(raw);
        }

        return copyWithIntent(raw, HrQueryIntent.KNOWLEDGE);
    }

    private IntentClassification ensureNamedEmployee(IntentClassification raw) {
        if (raw.getIntent() == HrQueryIntent.NAMED_EMPLOYEE) {
            return raw;
        }
        return IntentClassification.builder()
                .intent(HrQueryIntent.NAMED_EMPLOYEE)
                .employeeName(raw.getEmployeeName())
                .employeeTopic(raw.getEmployeeTopic())
                .source(raw.getSource())
                .build();
    }

    private IntentClassification copyWithIntent(IntentClassification raw, HrQueryIntent intent) {
        return IntentClassification.builder()
                .intent(intent)
                .employeeName(raw.getEmployeeName())
                .employeeTopic(raw.getEmployeeTopic())
                .source(raw.getSource())
                .build();
    }

    /** 复杂分析关键词优先 Text-to-SQL（与 LLM 结果取并集，防止漏判） */
    private boolean shouldForceTextToSql(String q, UserPrincipal user, HrQueryIntent current) {
        if (current == HrQueryIntent.KNOWLEDGE) {
            return false;
        }
        if (user.getRole() == UserRole.EMPLOYEE && !isPersonal(q)) {
            return false;
        }
        return containsAny(q,
                "对比", "比较", "排名", "超过", "低于", "同时", "并且", "且", "平均", "各部门",
                "找出", "筛选", "组合", "交叉", "分布", "占比", "排序", "大于", "小于");
    }

    private boolean canFallbackToTextToSql(String q, UserPrincipal user) {
        if (user.getRole() == UserRole.EMPLOYEE && !isPersonal(q)) {
            return false;
        }
        return true;
    }

    private boolean isPersonal(String q) {
        return q.contains("我的") || q.contains("我") && !containsAny(q, "我们", "部门", "公司", "团队", "哪个");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record CachedAnalysis(String question, Long userId, IntentClassification classification) {
        boolean matches(String question, Long userId) {
            return this.question.equals(question) && this.userId.equals(userId);
        }
    }
}
