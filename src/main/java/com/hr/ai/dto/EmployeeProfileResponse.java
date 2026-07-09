package com.hr.ai.dto;

import lombok.Data;

@Data
public class EmployeeProfileResponse {
    private String employeeId;
    private String name;
    private String departmentName;
    private String position;
    private Double leaveBalance;
    private Double overtimeHours;
    private String salaryBand;
}
