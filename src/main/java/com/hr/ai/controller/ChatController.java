package com.hr.ai.controller;

import com.hr.ai.dto.*;
import com.hr.ai.service.RagChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final long STREAM_START_DELAY_MS = 25L;

    private final RagChatService chatService;

    @PostMapping("/ask")
    public ApiResponse<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.ask(request));
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> emitter.complete());
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Runnable task = new DelegatingSecurityContextRunnable(() -> {
            try {
                RequestContextHolder.setRequestAttributes(requestAttributes);
                chatService.askStream(request, emitter);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        Executor delayedExecutor = CompletableFuture.delayedExecutor(STREAM_START_DELAY_MS, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(task, delayedExecutor);
        return emitter;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> sessions() {
        return ApiResponse.success(chatService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> messages(@PathVariable Long sessionId) {
        return ApiResponse.success(chatService.getMessages(sessionId));
    }
}
