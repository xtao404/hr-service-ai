package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnalyticsDashboardResponse {
    private Integer highRiskEmployeeCount;
    private Integer skillGapCount;
    private Double avgRecruitmentSuccessRate;
    private List<TurnoverPredictionResponse> topRisks;
    private List<SkillGapResponse> criticalGaps;
}
