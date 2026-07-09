package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ManagerReportResponse {
    private String reportTitle;
    private String summary;
    private String detail;
    private List<ReportMetric> metrics;
    private List<SourceReference> dataSources;
}
