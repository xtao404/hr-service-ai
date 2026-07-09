package com.hr.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.ai.dto.*;
import com.hr.ai.model.entity.ChatMessage;
import com.hr.ai.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatExchangePersistenceService {

    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveExchange(String question, PreparedChatResult result) {
        saveMessage(result.getSessionId(), "user", question, null, null, null, null);
        saveMessage(result.getSessionId(), "assistant", result.getAnswer(),
                result.getSources(), result.getCharts(), result.getTrace(), result.getActions());
    }

    private void saveMessage(Long sessionId, String role, String content,
                             List<SourceReference> sources, List<ChartConfig> charts,
                             QueryTrace trace, List<ActionSuggestion> actions) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        writeJson(message::setSources, sources);
        writeJson(message::setCharts, charts);
        writeJson(message::setTrace, trace);
        writeJson(message::setActions, actions);
        messageRepository.save(message);
    }

    private <T> void writeJson(java.util.function.Consumer<String> setter, T value) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        try {
            setter.accept(objectMapper.writeValueAsString(value));
        } catch (Exception ignored) {
        }
    }
}
