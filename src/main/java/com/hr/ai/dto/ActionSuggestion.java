package com.hr.ai.dto;

import lombok.Data;

@Data
public class ActionSuggestion {
    private String actionType;
    private String title;
    private String description;
    private String employeeId;
    private String employeeName;
}
