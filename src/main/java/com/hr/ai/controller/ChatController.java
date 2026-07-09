package com.hr.ai.controller;

import com.hr.ai.dto.*;
import com.hr.ai.service.RagChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService chatService;

    @PostMapping("/ask")
    public ApiResponse<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.ask(request));
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
