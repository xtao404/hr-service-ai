package com.hr.ai.service.intent;

import com.hr.ai.dto.IntentClassification;
import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * 规则引擎意图识别，作为 LLM 路由的 Mock/降级兜底。
 */
@Component
@RequiredArgsConstructor
public class RuleBasedIntentAnalyzer {

    private final EmployeeNameExtractor employeeNameExtractor;

    public IntentClassification classify(String question, UserPrincipal user) {
        String q = question.toLowerCase(Locale.ROOT);
        HrQueryIntent intent = classifyIntent(q, question, user);
        Optional<NamedEmployeeQuery> named = extractNamedEmployeeQuery(question);
        return IntentClassification.builder()
                .intent(intent)
                .employeeName(named.map(n -> n.resolvedNames().get(0)).orElse(null))
                .employeeTopic(named.map(NamedEmployeeQuery::getTopic).orElse(null))
                .employeeNames(named.map(NamedEmployeeQuery::resolvedNames).orElse(null))
                .source("rule")
                .build();
    }

    public Optional<NamedEmployeeQuery> extractNamedEmployeeQuery(String question) {
        return employeeNameExtractor.extractQuery(question);
    }

    private HrQueryIntent classifyIntent(String q, String question, UserPrincipal user) {
        if (isKnowledgeOnlyQuestion(q, question)) {
            return HrQueryIntent.KNOWLEDGE;
        }
        if (shouldUseTextToSql(q, user)) {
            return HrQueryIntent.TEXT_TO_SQL;
        }
        return resolvePresetIntent(q, question, user);
    }

    private HrQueryIntent resolvePresetIntent(String q, String question, UserPrincipal user) {
        if (isPersonal(q)) {
            if (containsAny(q, "假期", "年假", "余额", "请假")) return HrQueryIntent.PERSONAL_LEAVE;
            if (containsAny(q, "加班")) return HrQueryIntent.PERSONAL_OVERTIME;
            if (containsAny(q, "薪资", "工资", "薪酬")) return HrQueryIntent.PERSONAL_SALARY;
            if (containsAny(q, "绩效", "考核", "评级")) return HrQueryIntent.PERSONAL_PERFORMANCE;
            if (containsAny(q, "考勤", "迟到", "缺勤")) return HrQueryIntent.PERSONAL_ATTENDANCE;
            return HrQueryIntent.PERSONAL_PROFILE;
        }

        if (extractNamedEmployeeQuery(question).isPresent()) {
            return HrQueryIntent.NAMED_EMPLOYEE;
        }

        if (isManagerOrAbove(user.getRole())) {
            if (containsAny(q, "离职", "风险", "流失", "挽留")) return HrQueryIntent.DEPT_TURNOVER;
            if (containsAny(q, "加班", "工时")) return HrQueryIntent.DEPT_OVERTIME;
            if (containsAny(q, "人数", "在职", "headcount", "编制")) return HrQueryIntent.DEPT_HEADCOUNT;
            if (containsAny(q, "绩效", "考核", "表现")) return HrQueryIntent.DEPT_PERFORMANCE;
            if (containsAny(q, "薪酬", "工资", "薪资", "竞争力")) return HrQueryIntent.DEPT_SALARY;
            if (containsAny(q, "统计", "分析", "报告", "汇总", "概览", "整体", "公司")) {
                return HrQueryIntent.COMPANY_OVERVIEW;
            }
        }

        if (containsAny(q, "统计", "分析", "多少", "哪些", "排名", "对比", "汇总")) {
            if (user.getRole() == UserRole.EMPLOYEE) {
                return HrQueryIntent.KNOWLEDGE;
            }
            return HrQueryIntent.COMPANY_OVERVIEW;
        }

        return HrQueryIntent.KNOWLEDGE;
    }

    private boolean isKnowledgeOnlyQuestion(String q, String question) {
        if (isPersonal(q) || extractNamedEmployeeQuery(question).isPresent()) {
            return false;
        }

        if (containsAny(q, "有多少天") && containsAny(q, "年假", "假期", "病假", "事假", "调休")) {
            return true;
        }

        boolean hasPolicyKeyword = containsAny(q,
                "福利", "五险一金", "公积金", "社保", "体检", "补充福利", "商业保险",
                "调薪政策", "薪酬政策", "工资政策", "考勤制度", "考勤管理",
                "入职流程", "离职流程", "办理流程", "如何办理", "怎么办理",
                "如何申请", "怎么申请", "扣款", "制度", "规定", "政策",
                "食堂", "旅游", "礼品", "劳动合同");

        if (!hasPolicyKeyword) {
            return false;
        }

        return !containsAny(q,
                "员工", "人数", "在职", "加班", "绩效", "满意度", "风险", "headcount",
                "平均", "均值", "对比", "排名", "统计", "分布", "占比", "竞争力");
    }

    private boolean shouldUseTextToSql(String q, UserPrincipal user) {
        if (user.getRole() == UserRole.EMPLOYEE && !isPersonal(q)) {
            return false;
        }
        return containsAny(q,
                "对比", "比较", "排名", "最高", "最低", "超过", "低于", "高于", "大于", "小于",
                "同时", "并且", "且", "以及", "平均", "各部门", "哪个部门", "哪一", "分组",
                "列出", "找出", "筛选", "组合", "交叉", "top", "前", "后",
                "满意度", "绩效.*加班", "加班.*绩效",
                "分布", "占比", "比例", "趋势", "最多", "最少", "大于等于", "小于等于",
                "按部门", "按岗位", "按评级", "按等级", "排序", "从高到低", "从低到高");
    }

    private boolean isPersonal(String q) {
        return q.contains("我的") || q.contains("我") && !containsAny(q, "我们", "部门", "公司", "团队", "哪个");
    }

    private boolean isManagerOrAbove(UserRole role) {
        return role == UserRole.MANAGER || role == UserRole.HRBP || role == UserRole.HR_ADMIN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (keyword.contains(".*")) {
                if (text.matches(".*" + keyword + ".*")) return true;
            } else if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
