package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {
    private Long sessionId;
    private String answer;
    private List<SourceReference> sources;
}
