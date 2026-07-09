package com.hr.ai.service;

import com.hr.ai.config.LlmProperties;
import com.hr.ai.dto.SourceReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private static final String RAG_SYSTEM_PROMPT = """
            你是企业HR智能助手，负责回答员工关于考勤、假期、薪酬政策、福利、入离职流程等问题。

            回答要求：
            1. 严格基于提供的知识库参考资料作答，不得编造制度条款或数据
            2. 如果参考资料不足以回答问题，请明确告知并建议联系HR人工客服
            3. 使用简洁、专业、友好的中文回答
            4. 回答结构清晰，必要时使用分点说明
            5. 不要透露你不知道的信息，不要猜测
            """;

    private static final String REPORT_SYSTEM_PROMPT = """
            你是企业HR数据分析专家，负责为管理者解读HR数据并生成决策建议。

            回答要求：
            1. 严格基于提供的数据指标进行分析，不得编造数字
            2. 给出简明扼要的总结和可执行的建议
            3. 使用专业的中文，适合管理层阅读
            4. 如果数据不足以支撑结论，请如实说明
            """;

    private static final String DATA_SYSTEM_PROMPT = """
            你是企业HR智能助手，负责基于HR业务数据库的查询结果回答用户问题。

            回答要求：
            1. 严格基于提供的数据库查询结果作答，不得编造任何数字或人员信息
            2. 用自然、专业的中文组织回答，适合员工或管理者阅读
            3. 如果数据不足以回答问题，请明确告知
            4. 涉及薪酬等敏感数据时，仅复述查询结果中的内容，不额外推测
            5. 回答末尾注明"以上数据来源于HR业务系统"
            6. 不要在回答中展示或提及 SQL 语句
            """;

    private final LlmProperties llmProperties;
    private final QwenChatClient qwenChatClient;

    public String generateAnswer(String question, List<VectorStoreService.ScoredDocument> contexts) {
        if (llmProperties.isMockMode()) {
            logMockFallback();
            return buildMockAnswer(question, contexts);
        }

        if (contexts.isEmpty()) {
            return callQwen(RAG_SYSTEM_PROMPT,
                    "员工问题：" + question + "\n\n知识库参考资料：（未检索到相关文档）\n请告知用户知识库中暂无相关信息。");
        }

        String contextText = contexts.stream()
                .map(sd -> String.format("【%s】（相关度: %.0f%%）\n%s",
                        sd.document().getTitle(),
                        sd.similarity() * 100,
                        sd.document().getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        String userPrompt = String.format("""
                员工问题：%s

                知识库参考资料：
                %s

                请根据以上资料回答员工的问题。
                """, question, contextText);

        return callQwen(RAG_SYSTEM_PROMPT, userPrompt);
    }

    public String generateReport(String query, String dataContext) {
        if (llmProperties.isMockMode()) {
            logMockFallback();
            return "基于HR系统实时数据分析：\n" + dataContext + "\n\n针对「" + query + "」的分析结论已生成，"
                    + "建议关注部门加班趋势与行业薪酬对标差距。";
        }

        String userPrompt = String.format("""
                管理者查询：%s

                HR系统数据：
                %s

                请生成一份简洁的分析报告，包含：核心发现、数据解读、行动建议。
                """, query, dataContext);

        return callQwen(REPORT_SYSTEM_PROMPT, userPrompt);
    }

    public String generateDataAnswer(String question, String dataContext, String dataSource) {
        if (dataContext == null || dataContext.isBlank()) {
            return "未能从数据库中查询到相关数据，请确认已导入测试数据脚本 scripts/mysql/hr_test_data.sql。";
        }

        if (llmProperties.isMockMode()) {
            logMockFallback();
            return String.format("根据HR业务系统数据（%s），针对您的问题「%s」，查询结果如下：\n\n%s\n\n以上数据来源于HR业务系统。",
                    dataSource, question, dataContext);
        }

        String userPrompt = String.format("""
                用户问题：%s

                数据来源：%s

                数据库查询结果：
                %s

                请根据以上真实数据回答用户的问题。
                """, question, dataSource, dataContext);

        return callQwen(DATA_SYSTEM_PROMPT, userPrompt);
    }

    private String callQwen(String systemPrompt, String userPrompt) {
        try {
            return qwenChatClient.chat(systemPrompt, userPrompt);
        } catch (RuntimeException e) {
            log.warn("Qwen 调用失败，回退到 Mock 模式: {}", e.getMessage());
            return "【大模型服务暂时不可用，以下为知识库原文参考】\n\n" + userPrompt;
        }
    }

    private void logMockFallback() {
        if ("mock".equalsIgnoreCase(llmProperties.getProvider())) {
            log.debug("当前为 Mock 模式（provider=mock）");
        } else {
            log.warn("未配置 LLM API Key，使用 Mock 模式。请设置环境变量 LLM_API_KEY 或 hr.ai.llm.api-key");
        }
    }

    private String buildMockAnswer(String question, List<VectorStoreService.ScoredDocument> contexts) {
        if (contexts.isEmpty()) {
            return "抱歉，我在知识库中没有找到与您问题相关的信息。建议您联系HR人工客服，或尝试换个方式描述您的问题。";
        }
        VectorStoreService.ScoredDocument top = contexts.get(0);
        String topTitle = top.document().getTitle();
        String relevantSnippet = extractRelevantSnippet(top.document().getContent(), question);
        return String.format(
                "根据公司制度文件《%s》及相关政策说明，针对您的问题「%s」，答复如下：\n\n%s\n\n"
                        + "以上信息来源于公司内部知识库，如有疑问请联系HR部门确认。",
                topTitle, question, relevantSnippet);
    }

    private String extractRelevantSnippet(String content, String question) {
        for (String keyword : List.of("年假", "假期", "迟到", "考勤", "薪酬", "工资", "福利", "五险一金", "入职", "离职")) {
            if (question.contains(keyword)) {
                int idx = content.indexOf(keyword);
                if (idx >= 0) {
                    int start = Math.max(0, idx - 30);
                    int end = Math.min(content.length(), idx + 200);
                    String snippet = content.substring(start, end);
                    if (start > 0) {
                        snippet = "..." + snippet;
                    }
                    if (end < content.length()) {
                        snippet = snippet + "...";
                    }
                    return snippet;
                }
            }
        }
        return extractSnippet(content, 500);
    }

    private String extractSnippet(String content, int maxLen) {
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }

    public List<SourceReference> toSourceReferences(List<VectorStoreService.ScoredDocument> scored) {
        return scored.stream().map(sd -> {
            SourceReference ref = new SourceReference();
            ref.setDocumentId(sd.document().getId());
            ref.setTitle(sd.document().getTitle());
            ref.setSnippet(extractSnippet(sd.document().getContent(), 150));
            ref.setRelevance(sd.similarity());
            return ref;
        }).collect(Collectors.toList());
    }
}
