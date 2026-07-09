package com.hr.ai.dto;

import lombok.Data;

@Data
public class EmployeeProfileResponse {
    private String employeeId;
    private String name;
    private String departmentName;
    private String position;
    private String hireDate;
    private String education;
    private Double satisfactionScore;
    private Double leaveBalance;
    private Double overtimeHours;
    private Integer lateCount;
    private Double absentDays;
    private String salaryBand;
    private Double baseSalary;
    private String performanceRating;
    private Double performanceScore;
    private Double riskScore;
    private String riskLevel;
    private String riskFactors;
    private String riskRecommendation;
}
