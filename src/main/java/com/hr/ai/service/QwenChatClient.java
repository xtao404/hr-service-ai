package com.hr.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hr.ai.config.LlmProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * 通义千问 Qwen API 客户端（OpenAI 兼容模式）
 * 文档: https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-by-calling-api
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenChatClient {

    private final LlmProperties llmProperties;

    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, llmProperties.getTemperature());
    }

    public String chat(String systemPrompt, String userPrompt, double temperature) {
        ChatRequest request = new ChatRequest();
        request.setModel(llmProperties.getModel());
        request.setTemperature(temperature);
        request.setMessages(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        ));

        RestClient client = RestClient.builder()
                .requestFactory(createRequestFactory())
                .build();

        try {
            ChatResponse response = client.post()
                    .uri(llmProperties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + llmProperties.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("Qwen API 返回空响应");
            }
            String content = response.getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("Qwen API 返回内容为空");
            }
            return content.trim();
        } catch (RestClientException e) {
            log.error("调用 Qwen API 失败: {}", e.getMessage());
            throw new RuntimeException("调用 Qwen 大模型失败，请检查 API Key 和网络连接", e);
        }
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = llmProperties.getTimeoutSeconds() * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }

    @Data
    static class ChatRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
    }

    @Data
    static class Message {
        private String role;
        private String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChatResponse {
        private List<Choice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        private Message message;
    }
}
