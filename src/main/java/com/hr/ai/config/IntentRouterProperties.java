package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.intent-router")
public class IntentRouterProperties {

    /** llm：大模型意图识别（默认）；rule：仅规则引擎（测试/降级） */
    private String mode = "llm";

    /** LLM 识别失败或未配置 API Key 时，是否回退到规则引擎 */
    private boolean fallbackToRule = true;

    /** 意图识别 temperature（越低越稳定） */
    private double temperature = 0.1;

    public boolean isLlmMode() {
        return !"rule".equalsIgnoreCase(mode);
    }
}
