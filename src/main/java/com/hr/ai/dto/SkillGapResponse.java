package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class SkillGapResponse {
    private String positionName;
    private String requiredSkill;
    private Integer currentSupply;
    private Integer projectedDemand;
    private Integer gapCount;
    private Integer priority;
    private String recommendation;
}
