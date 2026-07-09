package com.hr.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionTaskRequest {
    @NotBlank
    private String actionType;
    private String employeeId;
    private String employeeName;
    @NotBlank
    private String title;
    private String description;
}
