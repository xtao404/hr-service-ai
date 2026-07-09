package com.hr.ai.dto;

import lombok.Data;

@Data
public class SourceReference {
    private Long documentId;
    private String title;
    private String snippet;
    private Double relevance;
}
