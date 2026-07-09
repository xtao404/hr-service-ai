package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.llm")
public class LlmProperties {

    /** mock | qwen | openai | deepseek */
    private String provider = "qwen";

    private String apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private String apiKey = "";

    private String model = "qwen-plus";

    private double temperature = 0.3;

    private int timeoutSeconds = 60;

    public boolean isMockMode() {
        return "mock".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank();
    }
}
