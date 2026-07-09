package com.hr.ai.dto;

import lombok.Data;

@Data
public class ActionTaskResponse {
    private String taskId;
    private String status;
    private String message;
}
