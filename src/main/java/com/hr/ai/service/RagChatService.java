package com.hr.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.ai.dto.*;
import com.hr.ai.model.entity.*;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.repository.*;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagChatService {

    private final VectorStoreService vectorStoreService;
    private final LlmService llmService;
    private final HrDataQueryService hrDataQueryService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final ChartDataService chartDataService;

    @Transactional
    public ChatResponse ask(ChatRequest request) {
        UserPrincipal user = permissionService.currentUser();
        String question = request.getQuestion();

        ChatSession session = resolveSession(request.getSessionId(), user.getId(), question);

        HrQueryIntent intent = hrDataQueryService.detectIntent(question, user);

        if (hrDataQueryService.isDatabaseIntent(intent)) {
            return handleDatabaseQuery(user, session, question, intent);
        }

        List<VectorStoreService.ScoredDocument> contexts = vectorStoreService.search(question);
        String answer = llmService.generateAnswer(question, contexts);
        List<SourceReference> sources = llmService.toSourceReferences(contexts);

        saveMessage(session.getId(), "user", question, null, null);
        saveMessage(session.getId(), "assistant", answer, sources, null);

        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getId());
        response.setAnswer(answer);
        response.setSources(sources);
        return response;
    }

    private ChatResponse handleDatabaseQuery(UserPrincipal user, ChatSession session,
                                             String question, HrQueryIntent intent) {
        HrDataContext dataContext = hrDataQueryService.query(question, user);
        String answer = llmService.generateDataAnswer(question, dataContext.getDataText(), dataContext.getDataSource());

        SourceReference source = new SourceReference();
        source.setTitle(dataContext.getDataSource());
        if (dataContext.getGeneratedSql() != null) {
            source.setSnippet("SQL: " + truncate(dataContext.getGeneratedSql(), 100));
        } else {
            source.setSnippet(truncate(dataContext.getDataText(), 120));
        }
        source.setRelevance(1.0);
        List<SourceReference> sources = List.of(source);

        List<ChartConfig> charts = chartDataService.buildCharts(
                dataContext.getQueryRows(), dataContext.getChartTitle());

        saveMessage(session.getId(), "user", question, null, null);
        saveMessage(session.getId(), "assistant", answer, sources, charts);

        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getId());
        response.setAnswer(answer);
        response.setSources(sources);
        response.setCharts(charts.isEmpty() ? null : charts);
        return response;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private ChatSession resolveSession(Long sessionId, Long userId, String question) {
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .filter(s -> s.getUserId().equals(userId))
                    .orElseGet(() -> createSession(userId, question));
        }
        return createSession(userId, question);
    }

    private ChatSession createSession(Long userId, String question) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(question.length() > 30 ? question.substring(0, 30) + "..." : question);
        return sessionRepository.save(session);
    }

    private void saveMessage(Long sessionId, String role, String content,
                             List<SourceReference> sources, List<ChartConfig> charts) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        if (sources != null && !sources.isEmpty()) {
            try {
                message.setSources(objectMapper.writeValueAsString(sources));
            } catch (Exception ignored) {
            }
        }
        if (charts != null && !charts.isEmpty()) {
            try {
                message.setCharts(objectMapper.writeValueAsString(charts));
            } catch (Exception ignored) {
            }
        }
        messageRepository.save(message);
    }

    public List<ChatSessionResponse> listSessions() {
        UserPrincipal user = permissionService.currentUser();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(s -> {
                    ChatSessionResponse r = new ChatSessionResponse();
                    r.setId(s.getId());
                    r.setTitle(s.getTitle());
                    r.setCreatedAt(s.getCreatedAt().format(fmt));
                    return r;
                }).collect(Collectors.toList());
    }

    public List<ChatMessageResponse> getMessages(Long sessionId) {
        UserPrincipal user = permissionService.currentUser();
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        if (!session.getUserId().equals(user.getId())) {
            throw new RuntimeException("无权访问该会话");
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(m -> {
                    ChatMessageResponse r = new ChatMessageResponse();
                    r.setId(m.getId());
                    r.setRole(m.getRole());
                    r.setContent(m.getContent());
                    r.setCreatedAt(m.getCreatedAt().format(fmt));
                    if (m.getSources() != null) {
                        try {
                            r.setSources(objectMapper.readValue(m.getSources(), new TypeReference<List<SourceReference>>() {}));
                        } catch (Exception ignored) {
                        }
                    }
                    if (m.getCharts() != null) {
                        try {
                            r.setCharts(objectMapper.readValue(m.getCharts(), new TypeReference<List<ChartConfig>>() {}));
                        } catch (Exception ignored) {
                        }
                    }
                    return r;
                }).collect(Collectors.toList());
    }
}
