package com.hr.ai.service.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.ai.config.IntentRouterProperties;
import com.hr.ai.config.LlmProperties;
import com.hr.ai.dto.IntentClassification;
import com.hr.ai.model.enums.EmployeeQueryTopic;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.QwenChatClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmIntentAnalyzer {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final QwenChatClient qwenChatClient;
    private final LlmProperties llmProperties;
    private final IntentRouterProperties intentRouterProperties;
    private final IntentRouterPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public IntentClassification classify(String question, UserPrincipal user) {
        if (llmProperties.isMockMode()) {
            throw new IllegalStateException("LLM Mock 模式，跳过意图识别");
        }
        String systemPrompt = promptBuilder.buildSystemPrompt(user);
        String userPrompt = promptBuilder.buildUserPrompt(question);
        String raw = qwenChatClient.chat(systemPrompt, userPrompt, intentRouterProperties.getTemperature());
        IntentClassification parsed = parseResponse(raw);
        parsed.setSource("llm");
        return parsed;
    }

    private IntentClassification parseResponse(String raw) {
        String json = extractJson(raw);
        try {
            LlmIntentPayload payload = objectMapper.readValue(json, LlmIntentPayload.class);
            HrQueryIntent intent = parseIntent(payload.getIntent());
            EmployeeQueryTopic topic = parseTopic(payload.getEmployeeTopic());
            return IntentClassification.builder()
                    .intent(intent)
                    .employeeName(normalize(payload.getEmployeeName()))
                    .employeeTopic(topic)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 LLM 意图 JSON: " + json, e);
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LLM 返回为空");
        }
        Matcher matcher = JSON_BLOCK.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }

    private HrQueryIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("intent 为空");
        }
        try {
            return HrQueryIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知 intent: " + value, e);
        }
    }

    private EmployeeQueryTopic parseTopic(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return EmployeeQueryTopic.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("未知 employeeTopic: {}，默认 PROFILE", value);
            return EmployeeQueryTopic.PROFILE;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LlmIntentPayload {
        private String intent;
        private String employeeName;
        private String employeeTopic;
    }
}
