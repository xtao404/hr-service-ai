package com.hr.ai.dto;

import com.hr.ai.model.enums.RiskLevel;
import lombok.Data;

import java.util.List;

@Data
public class TurnoverPredictionResponse {
    private String employeeId;
    private String employeeName;
    private String departmentName;
    private Double riskScore;
    private RiskLevel riskLevel;
    private List<String> factors;
    private String recommendation;
}
