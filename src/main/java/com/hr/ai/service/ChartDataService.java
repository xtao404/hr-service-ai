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
            "headcount", "satisfaction", "满意度", "数值", "value", "amount", "rate", "招聘"
    );

    /** 仅当指标本身表示占比/构成时使用饼图 */
    private static final Set<String> PIE_HINTS = Set.of("占比", "比例", "percent", "percentage");

    private static final Set<String> RECRUITMENT_HINTS = Set.of("招聘", "增编", "补员", "人力缺口", "headcount");

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
                ChartConfig chart = buildSingleRowMetricsChart(titleHint, normalized.get(0), numericCols);
                enrichChartMeta(chart, titleHint, numericCols);
                return List.of(chart);
            }
            ChartConfig chart = buildMultiRowChart(titleHint, normalized, labelCol, numericCols);
            return chart != null ? List.of(chart) : List.of();
        }

        if (normalized.size() == 1) {
            ChartConfig chart = buildSingleRowAllNumericChart(titleHint, normalized.get(0));
            if (chart != null) {
                enrichChartMeta(chart, titleHint, List.of());
            }
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
        List<String> chartMetrics = filterChartMetrics(titleHint, numericCols);
        if (chartMetrics.isEmpty()) {
            return null;
        }

        List<String> labels = rows.stream()
                .map(r -> formatLabel(r.get(labelCol)))
                .collect(Collectors.toList());

        List<ChartSeries> seriesList = new ArrayList<>();
        for (String numCol : chartMetrics) {
            ChartSeries series = new ChartSeries();
            series.setName(numCol);
            series.setData(rows.stream()
                    .map(r -> toDouble(r.get(numCol)))
                    .collect(Collectors.toList()));
            seriesList.add(series);
        }

        String primaryMetric = chartMetrics.get(0);
        String title = resolveTitle(titleHint, primaryMetric, chartMetrics);
        String type = resolveChartType(titleHint, primaryMetric, chartMetrics, rows.size(),
                seriesList.get(0).getData());

        ChartConfig config = new ChartConfig();
        config.setType(type);
        config.setTitle(title);
        config.setLabels(labels);
        config.setSeries(seriesList);
        enrichChartMeta(config, titleHint, chartMetrics);
        return config;
    }

    /** 招聘评估图表仅展示人数类指标，避免与加班时长混用同一 Y 轴 */
    private List<String> filterChartMetrics(String titleHint, List<String> numericCols) {
        if (!isRecruitmentContext(titleHint, numericCols)) {
            return numericCols;
        }
        List<String> peopleMetrics = numericCols.stream()
                .filter(col -> col.contains("人数") || col.contains("招聘")
                        || col.contains("headcount") || col.contains("编制"))
                .collect(Collectors.toCollection(ArrayList::new));
        return peopleMetrics.isEmpty() ? numericCols : peopleMetrics;
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
        config.setTitle(resolveTitle(titleHint, "数据概览", numericCols));
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
        config.setTitle(resolveTitle(titleHint, "数据概览", labels));
        config.setLabels(labels);
        config.setSeries(List.of(series));
        return config;
    }

    private void enrichChartMeta(ChartConfig config, String titleHint, List<String> numericCols) {
        if (config == null) {
            return;
        }
        String primaryMetric = numericCols.isEmpty()
                ? (config.getSeries().isEmpty() ? "" : config.getSeries().get(0).getName())
                : numericCols.get(0);

        if (isRecruitmentContext(titleHint, numericCols)) {
            config.setTitle("各部门招聘需求评估");
            config.setType("bar");
            config.setValueUnit("人");
            config.setSubtitle("柱状图对比各部门在职人数与建议补充人数；数值单位：人");
            return;
        }

        if ("pie".equals(config.getType())) {
            config.setValueUnit(inferUnit(primaryMetric));
            config.setSubtitle("扇区占比 = 该部门「" + primaryMetric + "」占全部部门合计的百分比");
            return;
        }

        config.setValueUnit(inferUnit(primaryMetric));
        if (numericCols.size() > 1) {
            config.setSubtitle("并列对比「" + String.join("」「", numericCols) + "」；单位：" + config.getValueUnit());
        } else if (primaryMetric != null && !primaryMetric.isBlank()) {
            config.setSubtitle("展示各部门「" + primaryMetric + "」对比；单位：" + config.getValueUnit());
        }
    }

    private boolean isRecruitmentContext(String titleHint, List<String> numericCols) {
        if (titleHint != null && matchesAny(titleHint, RECRUITMENT_HINTS)) {
            return true;
        }
        return numericCols.stream().anyMatch(col -> matchesAny(col, RECRUITMENT_HINTS));
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
        return prioritizeRecruitmentColumns(numericCols);
    }

    /** 招聘场景下优先展示「在职人数 + 建议招聘人数」，避免饼图误用第一列 */
    private List<String> prioritizeRecruitmentColumns(List<String> numericCols) {
        boolean hasRecruitment = numericCols.stream().anyMatch(col -> matchesAny(col, RECRUITMENT_HINTS));
        if (!hasRecruitment) {
            return numericCols;
        }
        List<String> ordered = new ArrayList<>();
        for (String col : numericCols) {
            if (col.contains("在职") || col.contains("headcount")) {
                ordered.add(col);
            }
        }
        for (String col : numericCols) {
            if (matchesAny(col, RECRUITMENT_HINTS) && !ordered.contains(col)) {
                ordered.add(col);
            }
        }
        for (String col : numericCols) {
            if (!ordered.contains(col)) {
                ordered.add(col);
            }
        }
        return ordered;
    }

    private String resolveChartType(String titleHint, String metricCol, List<String> numericCols,
                                  int rowCount, List<Double> values) {
        if (numericCols.size() > 1 || isRecruitmentContext(titleHint, numericCols)) {
            return "bar";
        }
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

    private String resolveTitle(String titleHint, String fallback, List<String> numericCols) {
        if (isRecruitmentContext(titleHint, numericCols)) {
            return "各部门招聘需求评估";
        }
        if (titleHint != null && !titleHint.isBlank()) {
            return titleHint;
        }
        return fallback + "统计";
    }

    private String inferUnit(String metricCol) {
        if (metricCol == null) {
            return "";
        }
        if (metricCol.contains("人数") || metricCol.contains("招聘") || metricCol.contains("headcount")) {
            return "人";
        }
        if (metricCol.contains("加班") || metricCol.contains("时长") || metricCol.contains("hour")) {
            return "小时";
        }
        if (metricCol.contains("薪酬") || metricCol.contains("月薪") || metricCol.contains("salary")) {
            return "元";
        }
        if (metricCol.contains("分") || metricCol.contains("score")) {
            return "分";
        }
        if (metricCol.contains("天")) {
            return "天";
        }
        return "";
    }

    private boolean matchesHint(String col, Set<String> hints) {
        String lower = col.toLowerCase(Locale.ROOT);
        return hints.stream().anyMatch(h -> lower.contains(h.toLowerCase(Locale.ROOT)));
    }

    private boolean matchesAny(String text, Set<String> hints) {
        return matchesHint(text, hints);
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
