package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChartConfig {
    /** bar | pie | line */
    private String type;
    private String title;
    private List<String> labels;
    private List<ChartSeries> series;
}
