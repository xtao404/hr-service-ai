package com.hr.ai.dto;

import lombok.Data;

@Data
public class ReportMetric {
    private String name;
    private String value;
    private String unit;
    private String trend;
}
