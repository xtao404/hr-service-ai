package com.hr.ai.service.texttosql;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将 SQL 结果列名映射为中文可读标签，便于 LLM 与前端展示。
 */
@Component
public class ColumnLabelMapper {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("employee_id", "员工编号"),
            Map.entry("name", "姓名"),
            Map.entry("gender", "性别"),
            Map.entry("dept_id", "部门编码"),
            Map.entry("dept_name", "部门名称"),
            Map.entry("position", "岗位"),
            Map.entry("hire_date", "入职日期"),
            Map.entry("status", "状态"),
            Map.entry("education", "学历"),
            Map.entry("satisfaction_score", "满意度"),
            Map.entry("quarter", "季度"),
            Map.entry("leave_balance", "年假余额(天)"),
            Map.entry("overtime_hours", "加班时长(小时)"),
            Map.entry("late_count", "迟到次数"),
            Map.entry("absent_days", "缺勤天数"),
            Map.entry("salary_band", "薪酬等级"),
            Map.entry("base_salary", "基本月薪(元)"),
            Map.entry("last_adjust_date", "最近调薪日期"),
            Map.entry("perf_year", "年份"),
            Map.entry("year", "年份"),
            Map.entry("rating", "绩效评级"),
            Map.entry("score", "绩效分数"),
            Map.entry("promotion_eligible", "可晋升"),
            Map.entry("risk_score", "离职风险分"),
            Map.entry("risk_level", "风险等级"),
            Map.entry("factors", "风险因素"),
            Map.entry("recommendation", "建议措施"),
            Map.entry("headcount", "在职人数"),
            Map.entry("manager_id", "负责人编号"),
            Map.entry("total_overtime", "总加班时长"),
            Map.entry("avg_overtime", "平均加班时长"),
            Map.entry("avg_salary", "平均月薪"),
            Map.entry("employee_count", "员工数")
    );

    public String toLabel(String columnName) {
        if (columnName == null) {
            return "";
        }
        String key = columnName.toLowerCase(Locale.ROOT);
        return LABELS.getOrDefault(key, columnName);
    }

    public Map<String, String> buildHeaderMap(Iterable<String> columns) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String col : columns) {
            headers.put(col, toLabel(col));
        }
        return headers;
    }
}
