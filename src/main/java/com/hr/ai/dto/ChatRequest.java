package com.hr.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank
    private String question;

    private Long sessionId;
}
