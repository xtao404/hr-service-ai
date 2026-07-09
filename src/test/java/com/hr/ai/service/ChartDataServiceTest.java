package com.hr.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartDataServiceTest {

    private ChartDataService chartDataService;

    @BeforeEach
    void setUp() {
        chartDataService = new ChartDataService(new com.hr.ai.service.texttosql.ColumnLabelMapper());
    }

    @Test
    void recruitmentQuery_usesBarChartWithClearSubtitle() {
        List<Map<String, Object>> rows = List.of(
                row("技术研发部", 8, 281.5, 6),
                row("市场营销部", 5, 120.0, 4),
                row("产品设计部", 4, 80.0, 2),
                row("人力资源部", 2, 20.0, 0)
        );

        var charts = chartDataService.buildCharts(rows, "各部门招聘需求评估");

        assertEquals(1, charts.size());
        var chart = charts.get(0);
        assertEquals("bar", chart.getType());
        assertEquals("各部门招聘需求评估", chart.getTitle());
        assertEquals("人", chart.getValueUnit());
        assertTrue(chart.getSubtitle().contains("在职人数"));
        assertTrue(chart.getSubtitle().contains("建议补充"));
        assertEquals(2, chart.getSeries().size());
        assertEquals("在职人数", chart.getSeries().get(0).getName());
        assertEquals("建议招聘人数", chart.getSeries().get(1).getName());
    }

    @Test
    void headcountOnly_noLongerDefaultsToPie() {
        List<Map<String, Object>> rows = List.of(
                Map.of("部门名称", "技术研发部", "在职人数", 8),
                Map.of("部门名称", "市场营销部", "在职人数", 5),
                Map.of("部门名称", "产品设计部", "在职人数", 4)
        );

        var chart = chartDataService.buildCharts(rows, "各部门在职人数").get(0);

        assertEquals("bar", chart.getType());
        assertTrue(chart.getSubtitle().contains("在职人数"));
    }

    @Test
    void explicitRatioMetric_stillUsesPie() {
        List<Map<String, Object>> rows = List.of(
                Map.of("部门名称", "技术研发部", "占比", 45),
                Map.of("部门名称", "市场营销部", "占比", 30),
                Map.of("部门名称", "产品设计部", "占比", 25)
        );

        var chart = chartDataService.buildCharts(rows, "部门占比").get(0);

        assertEquals("pie", chart.getType());
        assertTrue(chart.getSubtitle().contains("占全部部门合计的百分比"));
    }

    private static Map<String, Object> row(String dept, int headcount, double overtime, int hires) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("部门名称", dept);
        map.put("在职人数", headcount);
        map.put("总加班时长", overtime);
        map.put("建议招聘人数", hires);
        return map;
    }
}
