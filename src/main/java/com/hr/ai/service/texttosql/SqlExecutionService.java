package com.hr.ai.service.texttosql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ColumnLabelMapper columnLabelMapper;

    public List<Map<String, Object>> executeQuery(String sql) {
        log.info("执行 Text-to-SQL: {}", sql);
        try {
            return jdbcTemplate.queryForList(sql, Collections.emptyMap());
        } catch (Exception e) {
            log.error("SQL 执行失败: {}", e.getMessage());
            throw new SqlExecutionException("SQL 执行失败: " + e.getMessage(), e);
        }
    }

    public String formatResults(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "查询结果为空（0 行）";
        }

        Map<String, Object> firstRow = rows.get(0);
        List<String> columns = new ArrayList<>(firstRow.keySet());
        Map<String, String> headers = columnLabelMapper.buildHeaderMap(columns);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("共 " + rows.size() + " 行结果:");

        // 表头
        StringJoiner headerLine = new StringJoiner(" | ");
        for (String col : columns) {
            headerLine.add(headers.get(col));
        }
        sj.add(headerLine.toString());
        sj.add("-".repeat(Math.min(headerLine.length(), 80)));

        int index = 1;
        for (Map<String, Object> row : rows) {
            StringJoiner line = new StringJoiner(" | ");
            for (String col : columns) {
                Object val = row.get(col);
                line.add(formatValue(col, val));
            }
            sj.add(index++ + ". " + line);
        }
        return sj.toString();
    }

    private String formatValue(String column, Object value) {
        if (value == null) {
            return "-";
        }
        String col = column.toLowerCase(Locale.ROOT);
        if (col.contains("risk_score") && value instanceof Number num) {
            return String.format("%.0f%%", num.doubleValue() * 100);
        }
        if (col.contains("score") && col.contains("satisfaction") && value instanceof Number num) {
            return String.format("%.1f", num.doubleValue());
        }
        if (value instanceof Number num) {
            double d = num.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.format("%.0f", d);
            }
            return String.format("%.2f", d);
        }
        return value.toString();
    }
}
