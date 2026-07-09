package com.hr.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManagerReportRequest {

    @NotBlank
    private String query;

    private String period;
    private String departmentId;
}
