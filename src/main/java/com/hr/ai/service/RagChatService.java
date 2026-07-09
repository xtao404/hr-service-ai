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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
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
    private final QueryTraceBuilder queryTraceBuilder;
    private final ActionSuggestionService actionSuggestionService;
    private final ChatExchangePersistenceService chatExchangePersistenceService;

    @Transactional
    public ChatResponse ask(ChatRequest request) {
        PreparedChatResult result = prepareChat(request);
        chatExchangePersistenceService.saveExchange(request.getQuestion(), result);
        return toResponse(result);
    }

    public void askStream(ChatRequest request, SseEmitter emitter) {
        try {
            PreparedChatResult result = prepareChat(request);
            if (result.getTrace() != null) {
                sendEvent(emitter, "trace", result.getTrace());
            }
            streamAnswer(emitter, result.getAnswer());
            sendEvent(emitter, "done", toResponse(result));
            chatExchangePersistenceService.saveExchange(request.getQuestion(), result);
            emitter.complete();
        } catch (Exception e) {
            log.error("流式问答失败", e);
            try {
                String message = e.getMessage() != null ? e.getMessage() : "流式问答失败";
                emitter.send(SseEmitter.event().name("error").data(message));
                emitter.complete();
            } catch (IOException ignored) {
                emitter.complete();
            }
        }
    }

    private PreparedChatResult prepareChat(ChatRequest request) {
        UserPrincipal user = permissionService.currentUser();
        String question = request.getQuestion();
        ChatSession session = resolveSession(request.getSessionId(), user.getId(), question);
        HrQueryIntent intent = hrDataQueryService.detectIntent(question, user);

        if (hrDataQueryService.isDatabaseIntent(intent)) {
            return prepareDatabaseResult(user, session, question, intent);
        }
        return prepareKnowledgeResult(session, question);
    }

    private PreparedChatResult prepareKnowledgeResult(ChatSession session, String question) {
        List<VectorStoreService.ScoredDocument> contexts = vectorStoreService.search(question);
        String answer = llmService.generateAnswer(question, contexts);
        List<SourceReference> sources = llmService.toSourceReferences(contexts);
        QueryTrace trace = queryTraceBuilder.buildKnowledgeTrace();

        return PreparedChatResult.builder()
                .sessionId(session.getId())
                .answer(answer)
                .sources(sources)
                .trace(trace)
                .build();
    }

    private PreparedChatResult prepareDatabaseResult(UserPrincipal user, ChatSession session,
                                                       String question, HrQueryIntent intent) {
        HrDataContext dataContext = hrDataQueryService.query(question, user);
        String answer = llmService.generateDataAnswer(question, dataContext.getDataText(), dataContext.getDataSource());

        SourceReference source = new SourceReference();
        source.setTitle(dataContext.getDataSource());
        source.setSnippet(truncate(dataContext.getDataText(), 120));
        source.setRelevance(1.0);

        List<ChartConfig> charts = chartDataService.buildCharts(
                dataContext.getQueryRows(), dataContext.getChartTitle());
        QueryTrace trace = queryTraceBuilder.buildDataTrace(dataContext, user);
        List<ActionSuggestion> actions = actionSuggestionService.buildSuggestions(intent, dataContext, question);

        return PreparedChatResult.builder()
                .sessionId(session.getId())
                .answer(answer)
                .sources(List.of(source))
                .charts(charts.isEmpty() ? null : charts)
                .trace(trace)
                .actions(actions.isEmpty() ? null : actions)
                .build();
    }

    private ChatResponse toResponse(PreparedChatResult result) {
        ChatResponse response = new ChatResponse();
        response.setSessionId(result.getSessionId());
        response.setAnswer(result.getAnswer());
        response.setSources(result.getSources());
        response.setCharts(result.getCharts());
        response.setTrace(result.getTrace());
        response.setActions(result.getActions());
        return response;
    }

    private void streamAnswer(SseEmitter emitter, String answer) throws IOException, InterruptedException {
        if (answer == null || answer.isBlank()) {
            return;
        }
        int chunkSize = 12;
        for (int i = 0; i < answer.length(); i += chunkSize) {
            String chunk = answer.substring(i, Math.min(i + chunkSize, answer.length()));
            sendEvent(emitter, "chunk", chunk);
            Thread.sleep(18);
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(data)));
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
                    readJson(m.getSources(), new TypeReference<List<SourceReference>>() {}, r::setSources);
                    readJson(m.getCharts(), new TypeReference<List<ChartConfig>>() {}, r::setCharts);
                    readJson(m.getTrace(), new TypeReference<QueryTrace>() {}, r::setTrace);
                    readJson(m.getActions(), new TypeReference<List<ActionSuggestion>>() {}, r::setActions);
                    return r;
                }).collect(Collectors.toList());
    }

    private <T> void readJson(String json, TypeReference<T> type, java.util.function.Consumer<T> setter) {
        if (json == null) {
            return;
        }
        try {
            setter.accept(objectMapper.readValue(json, type));
        } catch (Exception ignored) {
        }
    }
}
