package com.hr.ai.service.texttosql;

import com.hr.ai.config.LlmProperties;
import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.HrQuestionAnalyzer;
import com.hr.ai.service.QwenChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextToSqlService {

    private final TextToSqlProperties textToSqlProperties;
    private final LlmProperties llmProperties;
    private final QwenChatClient qwenChatClient;
    private final SqlPromptBuilder sqlPromptBuilder;
    private final SqlSecurityValidator sqlSecurityValidator;
    private final SqlPermissionRewriter sqlPermissionRewriter;
    private final SqlExecutionService sqlExecutionService;
    private final PermissionService permissionService;
    private final HrQuestionAnalyzer questionAnalyzer;

    public HrDataContext query(String question, UserPrincipal user) {
        if (!textToSqlProperties.isEnabled()) {
            throw new IllegalArgumentException("Text-to-SQL 功能未启用");
        }

        String systemPrompt = sqlPromptBuilder.buildSystemPrompt(user);
        boolean canViewSalary = permissionService.canViewSalary();

        String rawSql = generateInitialSql(question, user, systemPrompt);
        String finalSql = null;
        List<Map<String, Object>> rows = null;

        int maxAttempts = textToSqlProperties.isSelfCorrectionEnabled() && !llmProperties.isMockMode()
                ? 1 + textToSqlProperties.getMaxRetries()
                : 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String sql = prepareSql(rawSql, user, canViewSalary);
                rows = sqlExecutionService.executeQuery(sql);
                finalSql = sql;
                if (attempt > 1) {
                    log.info("Text-to-SQL 第 {} 次尝试成功", attempt);
                }
                break;
            } catch (Exception e) {
                log.warn("Text-to-SQL 第 {} 次尝试失败: {}", attempt, e.getMessage());
                if (attempt >= maxAttempts || llmProperties.isMockMode()) {
                    throw e instanceof RuntimeException re ? re
                            : new RuntimeException(e.getMessage(), e);
                }
                String correctionPrompt = sqlPromptBuilder.buildCorrectionPrompt(
                        question, rawSql, e.getMessage());
                rawSql = qwenChatClient.chat(systemPrompt, correctionPrompt,
                        textToSqlProperties.getTemperature());
            }
        }

        String formatted = sqlExecutionService.formatResults(rows);

        HrDataContext context = new HrDataContext();
        context.setIntent(HrQueryIntent.TEXT_TO_SQL);
        context.setDataSource("HR业务数据库 (Text-to-SQL)");
        context.setQueryMethod("text-to-sql");
        context.setGeneratedSql(finalSql);
        context.setRowCount(rows.size());
        context.setQueryRows(rows);
        context.setChartTitle(inferChartTitle(question));
        context.setDataText(formatted);
        return context;
    }

    private String prepareSql(String rawSql, UserPrincipal user, boolean canViewSalary) {
        String sql = sqlSecurityValidator.validateAndSanitize(rawSql, user, canViewSalary);
        sql = sqlPermissionRewriter.injectPermissionFilter(sql, user);
        enforcePermissionFilter(sql, user);
        return sql;
    }

    private String generateInitialSql(String question, UserPrincipal user, String systemPrompt) {
        if (llmProperties.isMockMode()) {
            return generateMockSql(question, user);
        }
        return qwenChatClient.chat(systemPrompt, sqlPromptBuilder.buildUserPrompt(question),
                textToSqlProperties.getTemperature());
    }

    private void enforcePermissionFilter(String sql, UserPrincipal user) {
        String upper = sql.toUpperCase(Locale.ROOT);
        switch (user.getRole()) {
            case EMPLOYEE -> {
                if (!sql.contains(user.getEmployeeId())) {
                    throw new IllegalArgumentException(
                            "安全拦截：员工只能查询本人（" + user.getEmployeeId() + "）相关数据");
                }
                if (upper.contains("BIZ_SALARY")) {
                    throw new IllegalArgumentException("安全拦截：员工无权查询薪酬数据");
                }
            }
            case MANAGER -> {
                if (!sql.contains(user.getDepartmentId())) {
                    throw new IllegalArgumentException(
                            "安全拦截：部门经理只能查询本部门（" + user.getDepartmentId() + "）数据");
                }
                if (upper.contains("BIZ_SALARY")) {
                    throw new IllegalArgumentException("安全拦截：部门经理无权查询薪酬明细");
                }
            }
            default -> { }
        }
    }

    private String generateMockSql(String question, UserPrincipal user) {
        String q = question.toLowerCase(Locale.ROOT);
        var namedQuery = questionAnalyzer.extractNamedEmployeeQuery(question);
        if (namedQuery.isPresent() && user.getRole() != UserRole.EMPLOYEE) {
            return buildNamedEmployeeMockSql(namedQuery.get(), user);
        }

        if (isPersonalQuestion(q)) {
            return buildPersonalMockSql(q, user);
        }

        if (user.getRole() == UserRole.EMPLOYEE) {
            return "SELECT e.name AS 姓名, e.position AS 岗位, d.dept_name AS 部门, e.satisfaction_score AS 满意度 "
                    + "FROM biz_employee e "
                    + "JOIN biz_department d ON e.dept_id = d.dept_id "
                    + "WHERE e.employee_id = '" + user.getEmployeeId() + "' LIMIT 10";
        }

        String deptFilter = user.getRole() == UserRole.MANAGER
                ? " AND e.dept_id = '" + user.getDepartmentId() + "'"
                : "";

        if ((q.contains("部门") || q.contains("本季度")) && q.contains("加班")
                && !q.contains("各部门") && !q.contains("对比")) {
            return "SELECT e.name AS 姓名, a.overtime_hours AS 加班时长, a.late_count AS 迟到次数 "
                    + "FROM biz_employee e "
                    + "JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE a.quarter = '2026-Q1' AND e.status = 'ACTIVE'" + deptFilter
                    + " ORDER BY a.overtime_hours DESC LIMIT 20";
        }

        if (q.contains("对比") || q.contains("各部门") || q.contains("哪个部门")
                || (q.contains("排名") && !q.contains("我的"))) {
            return "SELECT d.dept_name AS 部门名称, SUM(a.overtime_hours) AS 总加班时长 "
                    + "FROM biz_department d JOIN biz_employee e ON d.dept_id = e.dept_id "
                    + "JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE a.quarter = '2026-Q1' AND e.status = 'ACTIVE'" + deptFilter
                    + " GROUP BY d.dept_name ORDER BY 总加班时长 DESC LIMIT 10";
        }
        if (q.contains("满意度")) {
            return "SELECT e.name AS 姓名, d.dept_name AS 部门, e.satisfaction_score AS 满意度 "
                    + "FROM biz_employee e JOIN biz_department d ON e.dept_id = d.dept_id "
                    + "WHERE e.status = 'ACTIVE' AND e.satisfaction_score < 7" + deptFilter
                    + " ORDER BY e.satisfaction_score ASC LIMIT 20";
        }
        if (q.contains("绩效") && q.contains("加班")) {
            return "SELECT e.name AS 姓名, p.rating AS 绩效评级, p.score AS 绩效分数, a.overtime_hours AS 加班时长 "
                    + "FROM biz_employee e "
                    + "JOIN biz_performance p ON e.employee_id = p.employee_id "
                    + "JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE p.rating = 'C' AND a.overtime_hours > 50 AND a.quarter = '2026-Q1'"
                    + deptFilter + " LIMIT 20";
        }
        if (q.contains("绩效")) {
            return "SELECT e.name AS 姓名, p.rating AS 绩效评级, p.score AS 绩效分数, e.satisfaction_score AS 满意度 "
                    + "FROM biz_employee e "
                    + "JOIN biz_performance p ON e.employee_id = p.employee_id "
                    + "WHERE e.status = 'ACTIVE'" + deptFilter + " ORDER BY p.score DESC LIMIT 20";
        }
        if (q.contains("离职") || q.contains("风险")) {
            return "SELECT e.name AS 姓名, d.dept_name AS 部门, r.risk_score AS 风险分, "
                    + "r.risk_level AS 风险等级, r.factors AS 风险因素 "
                    + "FROM biz_turnover_risk r JOIN biz_employee e ON r.employee_id = e.employee_id "
                    + "JOIN biz_department d ON e.dept_id = d.dept_id "
                    + "WHERE r.risk_level IN ('HIGH','CRITICAL')" + deptFilter
                    + " ORDER BY r.risk_score DESC LIMIT 20";
        }
        if (q.contains("人数") || q.contains("headcount") || q.contains("编制") || q.contains("在职")) {
            return "SELECT d.dept_name AS 部门名称, COUNT(e.id) AS 在职人数 "
                    + "FROM biz_department d "
                    + "LEFT JOIN biz_employee e ON d.dept_id = e.dept_id AND e.status = 'ACTIVE'"
                    + (user.getRole() == UserRole.MANAGER
                    ? " WHERE d.dept_id = '" + user.getDepartmentId() + "'" : "")
                    + " GROUP BY d.dept_name LIMIT 10";
        }
        if (q.contains("概览") || q.contains("整体") || (q.contains("公司") && q.contains("统计"))) {
            return "SELECT d.dept_name AS 部门名称, COUNT(e.id) AS 在职人数, "
                    + "COALESCE(SUM(a.overtime_hours), 0) AS 总加班时长 "
                    + "FROM biz_department d "
                    + "LEFT JOIN biz_employee e ON d.dept_id = e.dept_id AND e.status = 'ACTIVE' "
                    + "LEFT JOIN biz_attendance a ON e.employee_id = a.employee_id AND a.quarter = '2026-Q1' "
                    + "WHERE d.dept_id != 'D000'"
                    + (user.getRole() == UserRole.MANAGER
                    ? " AND d.dept_id = '" + user.getDepartmentId() + "'" : "")
                    + " GROUP BY d.dept_name LIMIT 10";
        }
        if (q.contains("薪酬") || q.contains("工资") || q.contains("薪资")) {
            if (user.getRole() == UserRole.MANAGER) {
                throw new IllegalArgumentException("部门经理无权查询薪酬数据");
            }
            return "SELECT d.dept_name AS 部门名称, AVG(s.base_salary) AS 平均月薪 "
                    + "FROM biz_salary s JOIN biz_employee e ON s.employee_id = e.employee_id "
                    + "JOIN biz_department d ON e.dept_id = d.dept_id "
                    + "WHERE e.status = 'ACTIVE' GROUP BY d.dept_name LIMIT 10";
        }
        return "SELECT d.dept_name AS 部门名称, COUNT(e.id) AS 在职人数 "
                + "FROM biz_department d "
                + "LEFT JOIN biz_employee e ON d.dept_id = e.dept_id AND e.status = 'ACTIVE'"
                + (user.getRole() == UserRole.MANAGER
                ? " WHERE d.dept_id = '" + user.getDepartmentId() + "'" : "")
                + " GROUP BY d.dept_name LIMIT 10";
    }

    private boolean isPersonalQuestion(String q) {
        return q.contains("我的") || q.contains("我") && !q.contains("我们") && !q.contains("部门")
                && !q.contains("公司") && !q.contains("团队");
    }

    private String buildPersonalMockSql(String q, UserPrincipal user) {
        String employeeId = user.getEmployeeId();
        if (q.contains("假期") || q.contains("年假") || q.contains("余额")) {
            return "SELECT e.name AS 姓名, a.leave_balance AS 年假余额, a.quarter AS 季度 "
                    + "FROM biz_employee e "
                    + "JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE e.employee_id = '" + employeeId + "' AND a.quarter = '2026-Q1' LIMIT 10";
        }
        if (q.contains("加班")) {
            return "SELECT e.name AS 姓名, a.overtime_hours AS 加班时长, a.quarter AS 季度 "
                    + "FROM biz_employee e "
                    + "JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE e.employee_id = '" + employeeId + "' AND a.quarter = '2026-Q1' LIMIT 10";
        }
        if (q.contains("考勤") || q.contains("迟到") || q.contains("缺勤")) {
            return "SELECT e.name AS 姓名, a.late_count AS 迟到次数, a.absent_days AS 缺勤天数, "
                    + "a.overtime_hours AS 加班时长 "
                    + "FROM biz_employee e "
                    + "JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE e.employee_id = '" + employeeId + "' AND a.quarter = '2026-Q1' LIMIT 10";
        }
        if (q.contains("绩效")) {
            return "SELECT e.name AS 姓名, p.rating AS 绩效评级, p.score AS 绩效分数 "
                    + "FROM biz_employee e "
                    + "JOIN biz_performance p ON e.employee_id = p.employee_id "
                    + "WHERE e.employee_id = '" + employeeId + "' LIMIT 10";
        }
        if (q.contains("薪资") || q.contains("工资") || q.contains("薪酬")) {
            if (user.getRole() == UserRole.EMPLOYEE) {
                throw new IllegalArgumentException("安全拦截：员工无权查询薪酬数据");
            }
            return "SELECT e.name AS 姓名, s.base_salary AS 基本月薪, s.salary_band AS 薪酬等级 "
                    + "FROM biz_employee e JOIN biz_salary s ON e.employee_id = s.employee_id "
                    + "WHERE e.employee_id = '" + employeeId + "' LIMIT 10";
        }
        return "SELECT e.name AS 姓名, e.position AS 岗位, d.dept_name AS 部门, e.satisfaction_score AS 满意度 "
                + "FROM biz_employee e "
                + "JOIN biz_department d ON e.dept_id = d.dept_id "
                + "WHERE e.employee_id = '" + employeeId + "' LIMIT 10";
    }

    private String buildNamedEmployeeMockSql(NamedEmployeeQuery namedQuery, UserPrincipal user) {
        String name = sanitizeEmployeeName(namedQuery.getEmployeeName());
        String deptFilter = user.getRole() == UserRole.MANAGER
                ? " AND e.dept_id = '" + user.getDepartmentId() + "'"
                : "";
        return switch (namedQuery.getTopic()) {
            case SALARY -> {
                if (user.getRole() == UserRole.MANAGER) {
                    throw new IllegalArgumentException("安全拦截：部门经理无权查询薪酬明细");
                }
                yield "SELECT e.name AS 姓名, s.base_salary AS 基本月薪, s.salary_band AS 薪酬等级 "
                        + "FROM biz_employee e JOIN biz_salary s ON e.employee_id = s.employee_id "
                        + "WHERE e.name = '" + name + "' AND e.status = 'ACTIVE'" + deptFilter + " LIMIT 10";
            }
            case LEAVE -> "SELECT e.name AS 姓名, a.leave_balance AS 年假余额 "
                    + "FROM biz_employee e JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE e.name = '" + name + "' AND a.quarter = '2026-Q1' AND e.status = 'ACTIVE'"
                    + deptFilter + " LIMIT 10";
            case OVERTIME -> "SELECT e.name AS 姓名, a.overtime_hours AS 累计加班 "
                    + "FROM biz_employee e JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE e.name = '" + name + "' AND a.quarter = '2026-Q1' AND e.status = 'ACTIVE'"
                    + deptFilter + " LIMIT 10";
            case ATTENDANCE -> "SELECT e.name AS 姓名, a.late_count AS 迟到次数, a.absent_days AS 缺勤天数, "
                    + "a.overtime_hours AS 加班时长 "
                    + "FROM biz_employee e JOIN biz_attendance a ON e.employee_id = a.employee_id "
                    + "WHERE e.name = '" + name + "' AND a.quarter = '2026-Q1' AND e.status = 'ACTIVE'"
                    + deptFilter + " LIMIT 10";
            case PERFORMANCE -> "SELECT e.name AS 姓名, p.rating AS 绩效评级, p.score AS 绩效分数 "
                    + "FROM biz_employee e JOIN biz_performance p ON e.employee_id = p.employee_id "
                    + "WHERE e.name = '" + name + "' AND e.status = 'ACTIVE'" + deptFilter + " LIMIT 10";
            case TURNOVER -> "SELECT e.name AS 姓名, r.risk_score AS 离职风险分, r.risk_level AS 风险等级 "
                    + "FROM biz_employee e JOIN biz_turnover_risk r ON e.employee_id = r.employee_id "
                    + "WHERE e.name = '" + name + "' AND e.status = 'ACTIVE'" + deptFilter + " LIMIT 10";
            case SATISFACTION -> "SELECT e.name AS 姓名, e.satisfaction_score AS 满意度 "
                    + "FROM biz_employee e "
                    + "WHERE e.name = '" + name + "' AND e.status = 'ACTIVE'" + deptFilter + " LIMIT 10";
            case PROFILE -> "SELECT e.name AS 姓名, e.position AS 岗位, d.dept_name AS 部门, e.satisfaction_score AS 满意度 "
                    + "FROM biz_employee e JOIN biz_department d ON e.dept_id = d.dept_id "
                    + "WHERE e.name = '" + name + "' AND e.status = 'ACTIVE'" + deptFilter + " LIMIT 10";
        };
    }

    private String sanitizeEmployeeName(String name) {
        if (name == null || !name.matches("[\\u4e00-\\u9fa5]{2,4}")) {
            throw new IllegalArgumentException("无效的员工姓名");
        }
        return name;
    }

    private String inferChartTitle(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("加班") && (q.contains("对比") || q.contains("排名") || q.contains("各部门"))) {
            return "各部门加班时长对比";
        }
        if (q.contains("离职") || q.contains("风险")) {
            return "离职风险分布";
        }
        if (q.contains("满意度")) {
            return "员工满意度分布";
        }
        if (q.contains("人数") || q.contains("编制")) {
            return "各部门在职人数";
        }
        if (q.contains("薪酬") || q.contains("工资") || q.contains("薪资")) {
            return "各部门平均薪酬";
        }
        if (q.contains("绩效")) {
            return "绩效数据对比";
        }
        return "查询结果统计";
    }
}
