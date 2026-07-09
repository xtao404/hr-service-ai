package com.hr.ai.service;

import com.hr.ai.dto.ChartConfig;
import com.hr.ai.dto.ChartSeries;
import com.hr.ai.service.texttosql.ColumnLabelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChartDataService {

    private static final Set<String> LABEL_HINTS = Set.of(
            "name", "姓名", "dept_name", "部门名称", "部门", "position", "岗位",
            "quarter", "季度", "year", "年份", "rating", "绩效评级", "指标", "label"
    );

    private static final Set<String> NUMERIC_HINTS = Set.of(
            "hours", "hour", "时长", "count", "次数", "人数", "score", "分数",
            "salary", "薪酬", "月薪", "risk", "风险", "balance", "余额", "days", "天数",
            "overtime", "加班", "late", "迟到", "absent", "缺勤", "avg", "平均", "total", "总",
            "headcount", "satisfaction", "满意度", "数值", "value", "amount", "rate"
    );

    private static final Set<String> PIE_HINTS = Set.of("人数", "headcount", "在职", "占比", "比例", "count");

    private final ColumnLabelMapper columnLabelMapper;

    public List<ChartConfig> buildCharts(List<Map<String, Object>> rows, String titleHint) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> normalized = normalizeRows(rows);
        if (normalized.isEmpty()) {
            return List.of();
        }

        String labelCol = findLabelColumn(normalized);
        List<String> numericCols = findNumericColumns(normalized, labelCol);

        if (labelCol != null && !numericCols.isEmpty()) {
            if (normalized.size() == 1 && numericCols.size() >= 2) {
                return List.of(buildSingleRowMetricsChart(titleHint, normalized.get(0), numericCols));
            }
            ChartConfig chart = buildMultiRowChart(titleHint, normalized, labelCol, numericCols);
            return chart != null ? List.of(chart) : List.of();
        }

        if (normalized.size() == 1) {
            ChartConfig chart = buildSingleRowAllNumericChart(titleHint, normalized.get(0));
            return chart != null ? List.of(chart) : List.of();
        }

        return List.of();
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String label = columnLabelMapper.toLabel(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Number || value == null || !isNumericString(value)) {
                    normalized.put(label, value);
                } else if (isNumericString(value)) {
                    normalized.put(label, parseNumber(value.toString()));
                } else {
                    normalized.put(label, value);
                }
            }
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private ChartConfig buildMultiRowChart(String titleHint, List<Map<String, Object>> rows,
                                           String labelCol, List<String> numericCols) {
        List<String> labels = rows.stream()
                .map(r -> formatLabel(r.get(labelCol)))
                .collect(Collectors.toList());

        List<ChartSeries> seriesList = new ArrayList<>();
        for (String numCol : numericCols) {
            ChartSeries series = new ChartSeries();
            series.setName(numCol);
            series.setData(rows.stream()
                    .map(r -> toDouble(r.get(numCol)))
                    .collect(Collectors.toList()));
            seriesList.add(series);
        }

        String title = resolveTitle(titleHint, numericCols.get(0));
        String type = resolveChartType(numericCols.get(0), rows.size(), seriesList.get(0).getData());

        ChartConfig config = new ChartConfig();
        config.setType(type);
        config.setTitle(title);
        config.setLabels(labels);
        config.setSeries(seriesList);
        return config;
    }

    private ChartConfig buildSingleRowMetricsChart(String titleHint, Map<String, Object> row,
                                                   List<String> numericCols) {
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (String col : numericCols) {
            labels.add(col);
            values.add(toDouble(row.get(col)));
        }

        ChartSeries series = new ChartSeries();
        series.setName("数值");
        series.setData(values);

        ChartConfig config = new ChartConfig();
        config.setType("bar");
        config.setTitle(resolveTitle(titleHint, "数据概览"));
        config.setLabels(labels);
        config.setSeries(List.of(series));
        return config;
    }

    private ChartConfig buildSingleRowAllNumericChart(String titleHint, Map<String, Object> row) {
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (isNumericValue(entry.getValue())) {
                labels.add(entry.getKey());
                values.add(toDouble(entry.getValue()));
            }
        }
        if (labels.isEmpty()) {
            return null;
        }

        ChartSeries series = new ChartSeries();
        series.setName("数值");
        series.setData(values);

        ChartConfig config = new ChartConfig();
        config.setType("bar");
        config.setTitle(resolveTitle(titleHint, "数据概览"));
        config.setLabels(labels);
        config.setSeries(List.of(series));
        return config;
    }

    private String findLabelColumn(List<Map<String, Object>> rows) {
        Set<String> columns = rows.get(0).keySet();
        for (String col : columns) {
            if (matchesHint(col, LABEL_HINTS)) {
                return col;
            }
        }
        for (String col : columns) {
            if (rows.stream().anyMatch(r -> r.get(col) != null && !isNumericValue(r.get(col)))) {
                return col;
            }
        }
        return null;
    }

    private List<String> findNumericColumns(List<Map<String, Object>> rows, String labelCol) {
        Set<String> columns = rows.get(0).keySet();
        List<String> numericCols = new ArrayList<>();
        for (String col : columns) {
            if (col.equals(labelCol)) {
                continue;
            }
            if (rows.stream().allMatch(r -> isNumericValue(r.get(col)))) {
                numericCols.add(col);
            }
        }
        if (numericCols.isEmpty()) {
            for (String col : columns) {
                if (!col.equals(labelCol) && matchesHint(col, NUMERIC_HINTS)) {
                    numericCols.add(col);
                }
            }
        }
        return numericCols;
    }

    private String resolveChartType(String metricCol, int rowCount, List<Double> values) {
        if (rowCount >= 2 && rowCount <= 8 && matchesHint(metricCol, PIE_HINTS)) {
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            if (sum > 0 && values.stream().allMatch(v -> v >= 0)) {
                return "pie";
            }
        }
        if (rowCount >= 3 && metricCol.contains("季度")) {
            return "line";
        }
        return "bar";
    }

    private String resolveTitle(String titleHint, String fallback) {
        if (titleHint != null && !titleHint.isBlank()) {
            return titleHint;
        }
        return fallback + "统计";
    }

    private boolean matchesHint(String col, Set<String> hints) {
        String lower = col.toLowerCase(Locale.ROOT);
        return hints.stream().anyMatch(h -> lower.contains(h.toLowerCase(Locale.ROOT)));
    }

    private boolean isNumericValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        return isNumericString(value);
    }

    private boolean isNumericString(Object value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(value.toString().replaceAll("[,%元小时天%]", "").trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Double parseNumber(String text) {
        try {
            return Double.parseDouble(text.replaceAll("[,%元小时天%]", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return parseNumber(value.toString());
    }

    private String formatLabel(Object value) {
        if (value == null) {
            return "-";
        }
        String text = value.toString();
        return text.length() > 12 ? text.substring(0, 12) + "…" : text;
    }
}
