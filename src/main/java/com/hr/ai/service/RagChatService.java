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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
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
        PreparedChatResult result = prepareChat(request, null);
        chatExchangePersistenceService.saveExchange(request.getQuestion(), result);
        return toResponse(result);
    }

    public void askStream(ChatRequest request, SseEmitter emitter) {
        try {
            sendProgress(emitter, "THINKING", "ANALYZE", "问题分析", "正在分析问题并判断最合适的回答路径...");
            PreparedChatResult result = prepareChat(request, trace -> sendSafeEvent(emitter, "trace", trace));
            if (result.getTrace() != null) {
                result.getTrace().setStatus("ANSWERING");
                result.getTrace().setStage("GENERATE");
                result.getTrace().setStageLabel("答案输出");
                result.getTrace().setProgressMessage("已完成分析，正在整理答案并输出...");
                sendEvent(emitter, "trace", result.getTrace());
            }
            streamAnswer(emitter, result.getAnswer());
            if (result.getTrace() != null) {
                result.getTrace().setStatus("DONE");
                result.getTrace().setStage("COMPLETE");
                result.getTrace().setStageLabel("回答完成");
                result.getTrace().setProgressMessage("已完成本次分析与回答。");
            }
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

    private PreparedChatResult prepareChat(ChatRequest request, Consumer<QueryTrace> progressListener) {
        UserPrincipal user = permissionService.currentUser();
        String question = request.getQuestion();
        ChatSession session = resolveSession(request.getSessionId(), user.getId(), question);
        HrQueryIntent intent = hrDataQueryService.detectIntent(question, user);
        emitIntentProgress(progressListener, intent);

        if (hrDataQueryService.isDatabaseIntent(intent)) {
            return prepareDatabaseResult(user, session, question, intent, progressListener);
        }
        return prepareKnowledgeResult(session, question, progressListener);
    }

    private PreparedChatResult prepareKnowledgeResult(ChatSession session, String question,
                                                      Consumer<QueryTrace> progressListener) {
        emitProgress(progressListener, buildProgressTrace(
                "KNOWLEDGE", "THINKING", "RETRIEVE", "知识检索",
                "正在检索知识库资料并筛选最相关的制度内容...",
                HrQueryIntent.KNOWLEDGE, "知识库 knowledge_documents", "rag"));
        List<VectorStoreService.ScoredDocument> contexts = vectorStoreService.search(question);
        emitProgress(progressListener, buildProgressTrace(
                "KNOWLEDGE", "THINKING", "GENERATE", "答案生成",
                contexts.isEmpty()
                        ? "未检索到直接命中的资料，正在结合已有知识组织答复..."
                        : "已找到相关制度资料，正在整理答案重点...",
                HrQueryIntent.KNOWLEDGE, "知识库 knowledge_documents", "rag"));
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
                                                     String question, HrQueryIntent intent,
                                                     Consumer<QueryTrace> progressListener) {
        emitProgress(progressListener, buildProgressTrace(
                resolveRouteType(intent), "THINKING", "QUERY", resolveStageLabel(intent),
                resolveProgressMessage(intent),
                intent, resolveDataSource(intent), resolveQueryMethod(intent)));
        HrDataContext dataContext = hrDataQueryService.query(question, user);
        emitProgress(progressListener, buildProgressTrace(
                "text-to-sql".equals(dataContext.getQueryMethod()) ? "TEXT_TO_SQL" : "PRESET_QUERY",
                "THINKING", "GENERATE", "答案生成",
                "已拿到结构化结果，正在组织自然语言回答并补充图表建议...",
                dataContext.getIntent(), dataContext.getDataSource(), dataContext.getQueryMethod()));
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

    private void sendSafeEvent(SseEmitter emitter, String name, Object data) {
        try {
            sendEvent(emitter, name, data);
        } catch (IOException ignored) {
        }
    }

    private void sendProgress(SseEmitter emitter, String status, String stage, String stageLabel, String message)
            throws IOException {
        QueryTrace trace = new QueryTrace();
        trace.setStatus(status);
        trace.setStage(stage);
        trace.setStageLabel(stageLabel);
        trace.setProgressMessage(message);
        sendEvent(emitter, "trace", trace);
    }

    private void emitIntentProgress(Consumer<QueryTrace> progressListener, HrQueryIntent intent) {
        emitProgress(progressListener, buildProgressTrace(
                resolveRouteType(intent), "THINKING", "ANALYZE", "路径判断",
                resolveIntentMessage(intent),
                intent, resolveDataSource(intent), resolveQueryMethod(intent)));
    }

    private void emitProgress(Consumer<QueryTrace> progressListener, QueryTrace trace) {
        if (progressListener != null && trace != null) {
            progressListener.accept(trace);
        }
    }

    private QueryTrace buildProgressTrace(String routeType, String status, String stage, String stageLabel,
                                          String progressMessage, HrQueryIntent intent,
                                          String dataSource, String queryMethod) {
        QueryTrace trace = new QueryTrace();
        trace.setRouteType(routeType);
        trace.setStatus(status);
        trace.setStage(stage);
        trace.setStageLabel(stageLabel);
        trace.setProgressMessage(progressMessage);
        if (intent != null) {
            trace.setIntent(intent.name());
        }
        trace.setIntentLabel(resolveIntentLabel(intent));
        trace.setDataSource(dataSource);
        trace.setQueryMethod(queryMethod);
        return trace;
    }

    private String resolveRouteType(HrQueryIntent intent) {
        if (intent == HrQueryIntent.KNOWLEDGE) {
            return "KNOWLEDGE";
        }
        return intent == HrQueryIntent.TEXT_TO_SQL ? "TEXT_TO_SQL" : "PRESET_QUERY";
    }

    private String resolveQueryMethod(HrQueryIntent intent) {
        return intent == HrQueryIntent.TEXT_TO_SQL ? "text-to-sql"
                : intent == HrQueryIntent.KNOWLEDGE ? "rag" : "preset";
    }

    private String resolveDataSource(HrQueryIntent intent) {
        return intent == HrQueryIntent.KNOWLEDGE ? "知识库 knowledge_documents" : "HR业务数据库";
    }

    private String resolveIntentMessage(HrQueryIntent intent) {
        return switch (intent) {
            case KNOWLEDGE -> "已识别为制度/政策类问题，准备检索知识库资料。";
            case TEXT_TO_SQL -> "已识别为复杂数据分析问题，准备生成安全查询方案。";
            default -> "已识别为业务数据查询问题，准备按权限读取 HR 数据。";
        };
    }

    private String resolveStageLabel(HrQueryIntent intent) {
        return intent == HrQueryIntent.TEXT_TO_SQL ? "数据分析" : "数据读取";
    }

    private String resolveProgressMessage(HrQueryIntent intent) {
        return switch (intent) {
            case TEXT_TO_SQL -> "正在分析指标口径并查询 HR 业务数据，这一步通常会稍慢一些...";
            case KNOWLEDGE -> "正在检索相关制度资料...";
            default -> "正在读取相关 HR 业务数据并进行权限校验...";
        };
    }

    private String resolveIntentLabel(HrQueryIntent intent) {
        if (intent == null) {
            return null;
        }
        return switch (intent) {
            case KNOWLEDGE -> "制度知识库检索";
            case PERSONAL_PROFILE -> "个人档案查询";
            case PERSONAL_LEAVE -> "个人假期查询";
            case PERSONAL_OVERTIME -> "个人加班查询";
            case PERSONAL_ATTENDANCE -> "个人考勤查询";
            case PERSONAL_SALARY -> "个人薪酬查询";
            case PERSONAL_PERFORMANCE -> "个人绩效查询";
            case NAMED_EMPLOYEE -> "指定员工查询";
            case DEPT_OVERTIME -> "部门加班统计";
            case DEPT_HEADCOUNT -> "部门人数统计";
            case DEPT_TURNOVER -> "部门离职风险";
            case DEPT_PERFORMANCE -> "部门绩效分析";
            case DEPT_SALARY -> "部门薪酬分析";
            case COMPANY_OVERVIEW -> "公司 HR 概览";
            case TEXT_TO_SQL -> "智能 SQL 分析";
        };
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
            throw new AccessDeniedException("无权访问该会话");
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
