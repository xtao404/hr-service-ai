package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatMessageResponse {
    private Long id;
    private String role;
    private String content;
    private List<SourceReference> sources;
    private String createdAt;
}
