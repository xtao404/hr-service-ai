package com.hr.ai.service;

import com.hr.ai.dto.ActionSuggestion;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.model.enums.HrQueryIntent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ActionSuggestionService {

    public List<ActionSuggestion> buildSuggestions(HrQueryIntent intent, HrDataContext context, String question) {
        List<ActionSuggestion> suggestions = new ArrayList<>();
        String q = question != null ? question.toLowerCase(Locale.ROOT) : "";

        if (intent == HrQueryIntent.DEPT_TURNOVER
                || intent == HrQueryIntent.TEXT_TO_SQL && (q.contains("离职") || q.contains("风险"))
                || q.contains("离职") || q.contains("风险")) {
            suggestions.addAll(buildRetentionSuggestions(context));
        }
        if (intent == HrQueryIntent.DEPT_OVERTIME
                || intent == HrQueryIntent.TEXT_TO_SQL && q.contains("加班") && q.contains("超过")
                || (q.contains("加班") && q.contains("超过"))) {
            suggestions.add(action("WORKLOAD", null, null,
                    "Review 部门工时负荷",
                    "建议 Review 团队排班与项目分配，必要时增加人手或调整交付节奏。"));
        }
        if (q.contains("满意度") && q.contains("低于")) {
            suggestions.add(action("ENGAGEMENT", null, null,
                    "启动员工满意度专项调研",
                    "建议对低满意度员工所在团队安排 1v1 沟通，识别具体痛点。"));
        }
        if (q.contains("技能") || q.contains("缺口")) {
            suggestions.add(action("TRAINING", null, null,
                    "制定关键技能内训计划",
                    "建议针对缺口技能启动内部培训或外部认证支持计划。"));
        }
        return suggestions;
    }

    private List<ActionSuggestion> buildRetentionSuggestions(HrDataContext context) {
        List<ActionSuggestion> suggestions = new ArrayList<>();
        if (context.getQueryRows() == null) {
            return suggestions;
        }
        int count = 0;
        for (Map<String, Object> row : context.getQueryRows()) {
            if (count >= 3) {
                break;
            }
            String name = stringVal(row, "姓名", "name");
            if (name == null) {
                continue;
            }
            Double riskScore = normalizeRiskScore(numberVal(row, "离职风险分", "risk_score", "风险分"));
            if (riskScore != null && riskScore < 60) {
                continue;
            }
            String employeeId = stringVal(row, "employee_id", "员工编号");
            suggestions.add(action("RETENTION", employeeId, name,
                    "安排 " + name + " 离职风险面谈",
                    "该员工存在较高离职风险，建议 7 日内由直属经理 + HRBP 联合面谈，了解诉求并制定挽留方案。"));
            count++;
        }
        if (suggestions.isEmpty()) {
            suggestions.add(action("RETENTION", null, null,
                    "启动高风险员工关怀计划",
                    "建议对高风险员工逐一安排 1v1 面谈，评估薪酬、发展、负荷等挽留杠杆。"));
        }
        return suggestions;
    }

    private ActionSuggestion action(String type, String employeeId, String employeeName,
                                    String title, String description) {
        ActionSuggestion suggestion = new ActionSuggestion();
        suggestion.setActionType(type);
        suggestion.setEmployeeId(employeeId);
        suggestion.setEmployeeName(employeeName);
        suggestion.setTitle(title);
        suggestion.setDescription(description);
        return suggestion;
    }

    private String stringVal(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().contains(key) && entry.getValue() != null) {
                    return entry.getValue().toString();
                }
            }
        }
        return null;
    }

    /** 统一为 0-100 分制：Text-to-SQL 原始值为 0-1，预设查询已 ×100。 */
    private Double normalizeRiskScore(Double score) {
        if (score == null) {
            return null;
        }
        return score <= 1 ? score * 100 : score;
    }

    private Double numberVal(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().contains(key) && entry.getValue() instanceof Number num) {
                    return num.doubleValue();
                }
            }
        }
        return null;
    }
}
