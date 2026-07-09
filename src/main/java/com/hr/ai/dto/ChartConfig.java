package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChartConfig {
    /** bar | pie | line */
    private String type;
    private String title;
    /** 图表说明，解释百分比或指标含义 */
    private String subtitle;
    /** 数值单位，如 人、小时、元 */
    private String valueUnit;
    private List<String> labels;
    private List<ChartSeries> series;
}
