package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecruitmentInsightResponse {
    private String positionName;
    private List<String> successTraits;
    private List<String> optimizationSuggestions;
    private Double successRate;
    private Integer sampleSize;
}
