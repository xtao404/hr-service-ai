package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.anti-hallucination")
public class AntiHallucinationProperties {

    /** 结构化 DB 查询结果优先模板直出，不经大模型润色 */
    private boolean skipLlmForStructuredData = true;

    /** 行数不超过此值时使用模板直出（大数据量避免 LLM 臆造汇总） */
    private int structuredDataMaxRows = 20;

    /** 大模型润色后校验答案数字是否均来自查询结果 */
    private boolean validateAnswerNumbers = true;

    /** 权限拦截、guard 类回答始终模板直出 */
    private boolean skipLlmForGuardResponses = true;
}
