package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.text-to-sql")
public class TextToSqlProperties {

    /** 是否启用 Text-to-SQL */
    private boolean enabled = true;

    /** 单次查询最大返回行数 */
    private int maxRows = 100;

    /** LLM 生成 SQL 时的 temperature（越低越稳定） */
    private double temperature = 0.1;

    /** 是否在 Prompt 中包含 Few-shot 示例 */
    private boolean fewShotEnabled = true;

    /** SQL 执行失败时是否启用 LLM 自纠错重试 */
    private boolean selfCorrectionEnabled = true;

    /** 自纠错最大重试次数（不含首次生成） */
    private int maxRetries = 2;
}
