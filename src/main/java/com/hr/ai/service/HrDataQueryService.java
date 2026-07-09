package com.hr.ai.service;

import com.hr.ai.config.PresetQueryProperties;
import com.hr.ai.dto.EmployeeProfileResponse;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.entity.biz.*;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.repository.biz.*;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.texttosql.TextToSqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class HrDataQueryService {

    private static final List<String> HIGH_RISK_LEVELS = List.of("HIGH", "CRITICAL", "MEDIUM");

    private final BizEmployeeRepository employeeRepository;
    private final BizDepartmentRepository departmentRepository;
    private final BizAttendanceRepository attendanceRepository;
    private final BizSalaryRepository salaryRepository;
    private final BizPerformanceRepository performanceRepository;
    private final BizTurnoverRiskRepository turnoverRiskRepository;
    private final HrQuestionAnalyzer questionAnalyzer;
    private final PermissionService permissionService;
    private final TextToSqlService textToSqlService;
    private final PresetQueryProperties presetQueryProperties;

    public HrQueryIntent detectIntent(String question, UserPrincipal user) {
        return questionAnalyzer.analyze(question, user);
    }

    public void clearAnalyzerCache() {
        questionAnalyzer.clearRequestCache();
    }

    public boolean isDatabaseIntent(HrQueryIntent intent) {
        return intent != HrQueryIntent.KNOWLEDGE;
    }

    public HrDataContext query(String question, UserPrincipal user) {
        Optional<NamedEmployeeQuery> multiNamed = questionAnalyzer.extractNamedEmployeeQuery(question, user)
                .filter(NamedEmployeeQuery::isMultiEmployee);
        if (multiNamed.isPresent()) {
            return queryMultipleNamedEmployees(question, user);
        }

        HrQueryIntent intent = questionAnalyzer.analyze(question, user);

        if (intent == HrQueryIntent.TEXT_TO_SQL) {
            return textToSqlService.query(question, user);
        }

        if (!presetQueryProperties.isEnabled()) {
            HrDataContext guard = guardWhenPresetDisabled(question, user);
            if (guard != null) {
                return guard;
            }
            if (intent == HrQueryIntent.NAMED_EMPLOYEE) {
                HrDataContext context = new HrDataContext();
                context.setIntent(HrQueryIntent.NAMED_EMPLOYEE);
                context.setDataSource("HR业务数据库");
                context.setQueryMethod("text-to-sql");
                fillNamedEmployee(context, user, question);
                applyManagerScopeNoteIfNeeded(context, question, user);
                applyResultMetadata(context);
                return context;
            }
            HrDataContext context = new HrDataContext();
            context.setIntent(HrQueryIntent.KNOWLEDGE);
            context.setDataSource("知识库 knowledge_documents");
            context.setQueryMethod("rag");
            context.setDataText("");
            return context;
        }

        HrDataContext context = new HrDataContext();
        context.setIntent(intent);
        context.setDataSource("HR业务数据库");
        context.setQueryMethod("preset");
        switch (intent) {
            case PERSONAL_PROFILE -> fillPersonalProfile(context, user);
            case PERSONAL_LEAVE -> fillPersonalLeave(context, user);
            case PERSONAL_OVERTIME -> fillPersonalOvertime(context, user);
            case PERSONAL_ATTENDANCE -> fillPersonalAttendance(context, user);
            case PERSONAL_SALARY -> fillPersonalSalary(context, user);
            case PERSONAL_PERFORMANCE -> fillPersonalPerformance(context, user);
            case NAMED_EMPLOYEE -> fillNamedEmployee(context, user, question);
            case DEPT_OVERTIME -> fillDeptOvertime(context, user);
            case DEPT_HEADCOUNT -> fillDeptHeadcount(context, user);
            case DEPT_TURNOVER -> fillDeptTurnover(context, user);
            case DEPT_PERFORMANCE -> fillDeptPerformance(context, user);
            case DEPT_SALARY -> fillDeptSalary(context, user);
            case COMPANY_OVERVIEW -> fillCompanyOverview(context, user);
            default -> context.setDataText("");
        }
        applyManagerScopeNoteIfNeeded(context, question, user);
        applyResultMetadata(context);
        return context;
    }

    private void applyResultMetadata(HrDataContext context) {
        if (context.getRowCount() == null && context.getQueryRows() != null) {
            context.setRowCount(context.getQueryRows().size());
        }
    }

    /** 多人指定员工查询优先走预设填充，避免 Text-to-SQL 只命中前缀相同的第一个姓名。 */
    private HrDataContext queryMultipleNamedEmployees(String question, UserPrincipal user) {
        HrDataContext context = new HrDataContext();
        context.setIntent(HrQueryIntent.NAMED_EMPLOYEE);
        context.setDataSource("HR业务数据库");
        context.setQueryMethod(presetQueryProperties.isEnabled() ? "preset" : "named-employee");
        fillNamedEmployee(context, user, question);
        applyManagerScopeNoteIfNeeded(context, question, user);
        applyResultMetadata(context);
        return context;
    }

    /**
     * preset 关闭时，对部分敏感/高误导问题做明确拦截，避免降级到知识库产生胡乱解释。
     * 返回 null 表示不拦截，按原逻辑降级。
     */
    private HrDataContext guardWhenPresetDisabled(String question, UserPrincipal user) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = question.toLowerCase();

        HrDataContext namedEmployeeGuard = guardNamedEmployeeWhenPresetDisabled(question, user);
        if (namedEmployeeGuard != null) {
            return namedEmployeeGuard;
        }

        boolean salary = q.contains("工资") || q.contains("薪资") || q.contains("薪酬");
        if (!salary) {
            return null;
        }

        // HRBP / HR_ADMIN 可走 Text-to-SQL（未必所有场景允许，但不在这里拦截）
        if (user.getRole() == UserRole.HRBP || user.getRole() == UserRole.HR_ADMIN) {
            return null;
        }

        HrDataContext context = new HrDataContext();
        context.setIntent(HrQueryIntent.PERSONAL_SALARY);
        context.setDataSource("权限拦截");
        context.setQueryMethod("guard");

        if (user.getRole() == UserRole.MANAGER) {
            context.setDataText("薪酬属于敏感信息，部门经理无权查询员工薪酬明细，请联系HRBP或HR管理员。");
            return context;
        }

        // EMPLOYEE：无论问自己还是他人，都不给出金额（避免把问题降级到知识库胡说）
        context.setDataText("薪酬属于敏感信息，普通员工无法通过系统查询具体金额。如需查看工资，请通过工资条/HR系统自助入口或联系HR。");
        return context;
    }

    /**
     * preset 关闭时，员工查他人业务数据不应降级到知识库，避免 RAG 误答。
     */
    private HrDataContext guardNamedEmployeeWhenPresetDisabled(String question, UserPrincipal user) {
        if (user.getRole() != UserRole.EMPLOYEE) {
            return null;
        }
        return questionAnalyzer.extractNamedEmployeeQuery(question, user)
                .filter(nq -> !isSelfNamedQuery(nq.getEmployeeName(), user))
                .map(nq -> {
                    HrDataContext context = new HrDataContext();
                    context.setIntent(HrQueryIntent.NAMED_EMPLOYEE);
                    context.setDataSource("权限拦截");
                    context.setQueryMethod("guard");
                    context.setDataText("无权查询其他员工的业务数据，如需了解同事信息请联系直属上级或HR。");
                    return context;
                })
                .orElse(null);
    }

    private boolean isSelfNamedQuery(String employeeName, UserPrincipal user) {
        if (employeeName == null || user.getName() == null) {
            return false;
        }
        return user.getName().contains(employeeName);
    }

    private void fillPersonalProfile(HrDataContext context, UserPrincipal user) {
        BizEmployee emp = requireEmployee(user.getEmployeeId());
        context.setDataText(formatEmployeeDetail(emp));
        context.setChartTitle("个人档案概览");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("姓名", emp.getName());
        row.put("满意度", emp.getSatisfactionScore());
        context.setQueryRows(List.of(row));
    }

    private void fillPersonalLeave(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryPersonalLeave(user));
        context.setChartTitle("假期余额");
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                user.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        if (att != null) {
            context.setQueryRows(List.of(Map.of("指标", "年假余额(天)", "数值", att.getLeaveBalance())));
        }
    }

    private void fillPersonalOvertime(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryPersonalOvertime(user));
        context.setChartTitle("加班时长");
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                user.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        if (att != null) {
            context.setQueryRows(List.of(Map.of("指标", "累计加班(小时)", "数值", att.getOvertimeHours())));
        }
    }

    private void fillPersonalAttendance(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryPersonalAttendance(user));
        context.setChartTitle("考勤统计");
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                user.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        if (att != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("迟到次数", att.getLateCount());
            row.put("缺勤天数", att.getAbsentDays());
            row.put("加班时长(小时)", att.getOvertimeHours());
            context.setQueryRows(List.of(row));
        }
    }

    private void fillPersonalSalary(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryPersonalSalary(user));
        context.setChartTitle("薪酬信息");
        if (!permissionService.canViewSalary() && user.getRole() == UserRole.EMPLOYEE) {
            return;
        }
        salaryRepository.findByEmployeeId(user.getEmployeeId()).ifPresent(salary -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("指标", "基本月薪(元)");
            row.put("数值", salary.getBaseSalary());
            context.setQueryRows(List.of(row));
        });
    }

    private void fillPersonalPerformance(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryPersonalPerformance(user));
        context.setChartTitle("绩效数据");
        performanceRepository.findFirstByEmployeeIdOrderByYearDescQuarterDesc(user.getEmployeeId())
                .ifPresent(perf -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("指标", "绩效分数");
                    row.put("数值", perf.getScore());
                    context.setQueryRows(List.of(row));
                });
    }

    private void fillNamedEmployee(HrDataContext context, UserPrincipal user, String question) {
        NamedEmployeeQuery namedQuery = questionAnalyzer.extractNamedEmployeeQuery(question, user).orElse(null);
        if (namedQuery == null) {
            context.setDataText("无法识别要查询的员工姓名，请使用如「张三的加班时长」或「赵六和赵六一的画像」这类表述。");
            return;
        }

        List<String> names = namedQuery.resolvedNames();
        if (names.size() > 1) {
            fillMultipleNamedEmployees(context, user, names, namedQuery.getTopic());
            return;
        }

        String name = names.get(0);
        List<BizEmployee> employees = employeeRepository.findByName(name);
        if (employees.isEmpty()) {
            context.setDataText("未找到员工「" + name + "」的人事档案。");
            return;
        }

        BizEmployee emp = employees.get(0);
        try {
            permissionService.checkEmployeeAccess(emp.getEmployeeId(), emp.getDeptId());
        } catch (AccessDeniedException e) {
            context.setDataText("无权查询该员工的数据。");
            return;
        }

        fillSingleNamedEmployee(context, user, emp, namedQuery.getTopic());
    }

    private void fillMultipleNamedEmployees(HrDataContext context, UserPrincipal user,
                                            List<String> names, com.hr.ai.model.enums.EmployeeQueryTopic topic) {
        StringJoiner textJoiner = new StringJoiner("\n\n---\n\n");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> denied = new ArrayList<>();

        for (String name : names) {
            List<BizEmployee> employees = employeeRepository.findByName(name);
            if (employees.isEmpty()) {
                notFound.add(name);
                continue;
            }
            BizEmployee emp = employees.get(0);
            try {
                permissionService.checkEmployeeAccess(emp.getEmployeeId(), emp.getDeptId());
            } catch (AccessDeniedException e) {
                denied.add(name);
                continue;
            }
            HrDataContext single = new HrDataContext();
            fillSingleNamedEmployee(single, user, emp, topic);
            textJoiner.add("【" + emp.getName() + "】\n" + single.getDataText());
            if (single.getQueryRows() != null) {
                rows.addAll(single.getQueryRows());
            }
        }

        StringJoiner result = new StringJoiner("\n\n");
        if (textJoiner.length() > 0) {
            result.add(textJoiner.toString());
        }
        for (String name : notFound) {
            result.add("未找到员工「" + name + "」的人事档案。");
        }
        for (String name : denied) {
            result.add("无权查询员工「" + name + "」的数据。");
        }
        if (result.length() == 0) {
            context.setDataText("未能查询到任何员工数据。");
            return;
        }

        context.setDataText(result.toString());
        context.setChartTitle("指定员工对比");
        context.setQueryRows(rows);
    }

    private void fillSingleNamedEmployee(HrDataContext context, UserPrincipal user, BizEmployee emp,
                                         com.hr.ai.model.enums.EmployeeQueryTopic topic) {
        switch (topic) {
            case SALARY -> fillNamedEmployeeSalary(context, user, emp);
            case LEAVE -> fillNamedEmployeeLeave(context, emp);
            case OVERTIME -> fillNamedEmployeeOvertime(context, emp);
            case ATTENDANCE -> fillNamedEmployeeAttendance(context, emp);
            case PERFORMANCE -> fillNamedEmployeePerformance(context, emp);
            case TURNOVER -> fillNamedEmployeeTurnover(context, emp);
            case SATISFACTION -> fillNamedEmployeeSatisfaction(context, emp);
            case PROFILE -> fillNamedEmployeeProfile(context, emp);
        }
    }

    private void fillNamedEmployeeProfile(HrDataContext context, BizEmployee emp) {
        context.setDataText(formatEmployeeDetail(emp));
        context.setChartTitle(emp.getName() + " - 个人档案");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("姓名", emp.getName());
        row.put("员工编号", emp.getEmployeeId());
        row.put("满意度", emp.getSatisfactionScore());
        context.setQueryRows(List.of(row));
    }

    private void fillNamedEmployeeLeave(HrDataContext context, BizEmployee emp) {
        String quarter = questionAnalyzer.currentQuarter();
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(emp.getEmployeeId(), quarter)
                .orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName() + " (" + emp.getEmployeeId() + ")");
        sj.add("季度: " + quarter);
        sj.add("年假余额: " + (att != null ? att.getLeaveBalance() + " 天" : "暂无记录"));
        context.setDataText(sj.toString());
        context.setChartTitle(emp.getName() + " - 假期余额");
        if (att != null) {
            context.setQueryRows(List.of(Map.of("姓名", emp.getName(), "年假余额(天)", att.getLeaveBalance())));
        }
    }

    private void fillNamedEmployeeOvertime(HrDataContext context, BizEmployee emp) {
        String quarter = questionAnalyzer.currentQuarter();
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(emp.getEmployeeId(), quarter)
                .orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        sj.add("季度: " + quarter);
        sj.add("累计加班: " + (att != null ? att.getOvertimeHours() + " 小时" : "暂无记录"));
        context.setDataText(sj.toString());
        context.setChartTitle(emp.getName() + " - 加班时长");
        if (att != null) {
            context.setQueryRows(List.of(Map.of("姓名", emp.getName(), "累计加班(小时)", att.getOvertimeHours())));
        }
    }

    private void fillNamedEmployeeAttendance(HrDataContext context, BizEmployee emp) {
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                emp.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        if (att != null) {
            sj.add("迟到次数: " + att.getLateCount());
            sj.add("缺勤天数: " + att.getAbsentDays());
            sj.add("加班时长: " + att.getOvertimeHours() + " 小时");
        } else {
            sj.add("暂无考勤记录");
        }
        context.setDataText(sj.toString());
        context.setChartTitle(emp.getName() + " - 考勤统计");
        if (att != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("姓名", emp.getName());
            row.put("迟到次数", att.getLateCount());
            row.put("缺勤天数", att.getAbsentDays());
            row.put("加班时长(小时)", att.getOvertimeHours());
            context.setQueryRows(List.of(row));
        }
    }

    private void fillNamedEmployeePerformance(HrDataContext context, BizEmployee emp) {
        BizPerformance perf = performanceRepository.findFirstByEmployeeIdOrderByYearDescQuarterDesc(
                emp.getEmployeeId()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        if (perf != null) {
            sj.add("最近绩效: " + perf.getYear() + " " + perf.getQuarter());
            sj.add("评级: " + perf.getRating() + "，分数: " + perf.getScore());
            sj.add("可晋升: " + (Boolean.TRUE.equals(perf.getPromotionEligible()) ? "是" : "否"));
        } else {
            sj.add("暂无绩效记录");
        }
        context.setDataText(sj.toString());
        context.setChartTitle(emp.getName() + " - 绩效数据");
        if (perf != null) {
            context.setQueryRows(List.of(Map.of("姓名", emp.getName(), "绩效分数", perf.getScore())));
        }
    }

    private void fillNamedEmployeeTurnover(HrDataContext context, BizEmployee emp) {
        BizTurnoverRisk risk = turnoverRiskRepository.findByEmployeeId(emp.getEmployeeId()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        if (risk != null) {
            sj.add(String.format("离职风险分: %.0f%%", risk.getRiskScore() * 100));
            sj.add("风险等级: " + risk.getRiskLevel());
            sj.add("风险因素: " + risk.getFactors());
            sj.add("建议: " + risk.getRecommendation());
        } else {
            sj.add("暂无离职风险记录");
        }
        context.setDataText(sj.toString());
        context.setChartTitle(emp.getName() + " - 离职风险");
        if (risk != null) {
            context.setQueryRows(List.of(Map.of("姓名", emp.getName(), "离职风险分", risk.getRiskScore() * 100)));
        }
    }

    private void fillNamedEmployeeSatisfaction(HrDataContext context, BizEmployee emp) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        sj.add("满意度评分: " + emp.getSatisfactionScore());
        context.setDataText(sj.toString());
        context.setChartTitle(emp.getName() + " - 满意度");
        context.setQueryRows(List.of(Map.of("姓名", emp.getName(), "满意度", emp.getSatisfactionScore())));
    }

    private void fillNamedEmployeeSalary(HrDataContext context, UserPrincipal user, BizEmployee emp) {
        context.setDataText(queryEmployeeSalary(user, emp));
        context.setChartTitle(emp.getName() + " - 薪酬信息");

        if (!permissionService.canViewSalary() || user.getRole() == UserRole.MANAGER) {
            return;
        }

        salaryRepository.findByEmployeeId(emp.getEmployeeId()).ifPresent(salary -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("姓名", emp.getName());
            row.put("基本月薪(元)", salary.getBaseSalary());
            row.put("薪酬等级", salary.getSalaryBand());
            context.setQueryRows(List.of(row));
        });
    }

    private String queryEmployeeSalary(UserPrincipal user, BizEmployee emp) {
        if (user.getRole() == UserRole.MANAGER) {
            return "部门经理无权查看员工薪酬明细，请联系HRBP或HR管理员。";
        }
        if (!permissionService.canViewSalary()) {
            if (user.getEmployeeId() != null && user.getEmployeeId().equals(emp.getEmployeeId())) {
                return "薪酬属于敏感信息，普通员工无法通过系统查询具体金额，请联系HR。";
            }
            return "薪酬属于敏感信息，无权查询其他员工的薪酬数据。";
        }

        BizSalary salary = salaryRepository.findByEmployeeId(emp.getEmployeeId()).orElse(null);
        if (salary == null) {
            return "员工「" + emp.getName() + "」暂无薪酬记录。";
        }
        String deptName = departmentRepository.findByDeptId(emp.getDeptId())
                .map(BizDepartment::getDeptName).orElse(emp.getDeptId());
        return String.format("员工: %s (%s)\n部门: %s\n薪酬等级: %s\n基本月薪: %.0f 元\n最近调薪: %s",
                emp.getName(), emp.getEmployeeId(), deptName,
                salary.getSalaryBand(), salary.getBaseSalary(), salary.getLastAdjustDate());
    }

    private void fillDeptOvertime(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryDeptOvertime(user));
        context.setChartTitle("部门加班统计");
        String deptId = resolveDeptId(user);
        String quarter = questionAnalyzer.currentQuarter();
        List<Map<String, Object>> rows = new ArrayList<>();
        employeeRepository.findByDeptIdAndStatus(deptId, "ACTIVE").forEach(emp ->
                attendanceRepository.findByEmployeeIdAndQuarter(emp.getEmployeeId(), quarter)
                        .ifPresent(att -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("姓名", emp.getName());
                            row.put("加班时长(小时)", att.getOvertimeHours());
                            row.put("迟到次数", att.getLateCount());
                            rows.add(row);
                        }));
        context.setQueryRows(rows);
    }

    private void fillDeptHeadcount(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryDeptHeadcount(user));
        context.setChartTitle("部门在职人数");
        String deptId = resolveDeptId(user);
        long count = employeeRepository.countByDeptIdAndStatus(deptId, "ACTIVE");
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);
        context.setQueryRows(List.of(Map.of("部门名称", deptName, "在职人数", count)));
    }

    private void fillDeptTurnover(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryDeptTurnover(user));
        context.setChartTitle("离职风险分布");
        String deptId = resolveDeptId(user);
        List<BizTurnoverRisk> risks = turnoverRiskRepository.findByDeptAndRiskLevels(deptId, HIGH_RISK_LEVELS);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizTurnoverRisk risk : risks) {
            BizEmployee emp = employeeRepository.findByEmployeeId(risk.getEmployeeId()).orElse(null);
            String name = emp != null ? emp.getName() : risk.getEmployeeId();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("姓名", name);
            row.put("离职风险分", risk.getRiskScore() * 100);
            rows.add(row);
        }
        context.setQueryRows(rows);
    }

    private void fillDeptPerformance(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryDeptPerformance(user));
        context.setChartTitle("部门绩效对比");
        String deptId = resolveDeptId(user);
        List<Map<String, Object>> rows = new ArrayList<>();
        employeeRepository.findByDeptIdAndStatus(deptId, "ACTIVE").forEach(emp ->
                performanceRepository.findFirstByEmployeeIdOrderByYearDescQuarterDesc(emp.getEmployeeId())
                        .ifPresent(perf -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("姓名", emp.getName());
                            row.put("绩效分数", perf.getScore());
                            row.put("满意度", emp.getSatisfactionScore());
                            rows.add(row);
                        }));
        context.setQueryRows(rows);
    }

    private void fillDeptSalary(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryDeptSalary(user));
        context.setChartTitle("薪酬对比");
        if (user.getRole() == UserRole.MANAGER) {
            return;
        }
        String deptId = resolveDeptId(user);
        Double deptAvg = salaryRepository.avgBaseSalaryByDept(deptId);
        Double companyAvg = salaryRepository.avgBaseSalary();
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("指标", deptName + "均值", "数值", deptAvg != null ? deptAvg : 0));
        rows.add(Map.of("指标", "全公司均值", "数值", companyAvg != null ? companyAvg : 0));
        context.setQueryRows(rows);
    }

    private void fillCompanyOverview(HrDataContext context, UserPrincipal user) {
        context.setDataText(queryCompanyOverview(user));
        context.setChartTitle("各部门概况");
        if (user.getRole() == UserRole.EMPLOYEE) {
            return;
        }
        String quarter = questionAnalyzer.currentQuarter();
        List<Map<String, Object>> rows = new ArrayList<>();
        departmentRepository.findAll().forEach(dept -> {
            if ("D000".equals(dept.getDeptId())) {
                return;
            }
            long count = employeeRepository.countByDeptIdAndStatus(dept.getDeptId(), "ACTIVE");
            Double overtime = attendanceRepository.sumOvertimeByDeptAndQuarter(dept.getDeptId(), quarter);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("部门名称", dept.getDeptName());
            row.put("在职人数", count);
            row.put("总加班时长", overtime != null ? overtime : 0);
            rows.add(row);
        });
        context.setQueryRows(rows);
    }

    public EmployeeProfileResponse getEmployeeProfile(String employeeId) {
        BizEmployee emp = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("未找到员工数据: " + employeeId));
        permissionService.checkEmployeeAccess(employeeId, emp.getDeptId());
        return buildEmployeeProfile(emp);
    }

    public EmployeeProfileResponse getEmployeeProfileByName(String name) {
        List<BizEmployee> employees = employeeRepository.findByName(name);
        if (employees.isEmpty()) {
            throw new RuntimeException("未找到员工: " + name);
        }
        BizEmployee emp = employees.get(0);
        permissionService.checkEmployeeAccess(emp.getEmployeeId(), emp.getDeptId());
        return buildEmployeeProfile(emp);
    }

    private EmployeeProfileResponse buildEmployeeProfile(BizEmployee emp) {
        String employeeId = emp.getEmployeeId();
        EmployeeProfileResponse profile = new EmployeeProfileResponse();
        profile.setEmployeeId(employeeId);
        profile.setName(emp.getName());
        profile.setPosition(emp.getPosition());
        profile.setHireDate(emp.getHireDate() != null ? emp.getHireDate().toString() : null);
        profile.setEducation(emp.getEducation());
        profile.setSatisfactionScore(emp.getSatisfactionScore());

        departmentRepository.findByDeptId(emp.getDeptId())
                .ifPresent(dept -> profile.setDepartmentName(dept.getDeptName()));

        attendanceRepository.findByEmployeeIdAndQuarter(employeeId, questionAnalyzer.currentQuarter())
                .ifPresent(att -> {
                    profile.setLeaveBalance(att.getLeaveBalance());
                    profile.setOvertimeHours(att.getOvertimeHours());
                    profile.setLateCount(att.getLateCount());
                    profile.setAbsentDays(att.getAbsentDays());
                });

        if (permissionService.canViewSalary()) {
            salaryRepository.findByEmployeeId(employeeId).ifPresent(sal -> {
                profile.setSalaryBand(sal.getSalaryBand());
                profile.setBaseSalary(sal.getBaseSalary());
            });
        }

        performanceRepository.findFirstByEmployeeIdOrderByYearDescQuarterDesc(employeeId)
                .ifPresent(perf -> {
                    profile.setPerformanceRating(perf.getRating());
                    profile.setPerformanceScore(perf.getScore());
                });

        turnoverRiskRepository.findByEmployeeId(employeeId).ifPresent(risk -> {
            profile.setRiskScore(risk.getRiskScore());
            profile.setRiskLevel(risk.getRiskLevel());
            profile.setRiskFactors(risk.getFactors());
            profile.setRiskRecommendation(risk.getRecommendation());
        });

        return profile;
    }

    private String queryPersonalProfile(UserPrincipal user) {
        return formatEmployeeDetail(requireEmployee(user.getEmployeeId()));
    }

    private String queryPersonalLeave(UserPrincipal user) {
        BizEmployee emp = requireEmployee(user.getEmployeeId());
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                user.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName() + " (" + emp.getEmployeeId() + ")");
        sj.add("季度: " + questionAnalyzer.currentQuarter());
        if (att != null) {
            sj.add("年假余额: " + att.getLeaveBalance() + " 天");
        } else {
            sj.add("年假余额: 暂无记录");
        }
        return sj.toString();
    }

    private String queryPersonalOvertime(UserPrincipal user) {
        BizEmployee emp = requireEmployee(user.getEmployeeId());
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                user.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        sj.add("季度: " + questionAnalyzer.currentQuarter());
        sj.add("累计加班: " + (att != null ? att.getOvertimeHours() + " 小时" : "暂无记录"));
        return sj.toString();
    }

    private String queryPersonalAttendance(UserPrincipal user) {
        BizEmployee emp = requireEmployee(user.getEmployeeId());
        BizAttendance att = attendanceRepository.findByEmployeeIdAndQuarter(
                user.getEmployeeId(), questionAnalyzer.currentQuarter()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        if (att != null) {
            sj.add("迟到次数: " + att.getLateCount());
            sj.add("缺勤天数: " + att.getAbsentDays());
            sj.add("加班时长: " + att.getOvertimeHours() + " 小时");
        }
        return sj.toString();
    }

    private String queryPersonalSalary(UserPrincipal user) {
        if (!permissionService.canViewSalary() && user.getRole() == UserRole.EMPLOYEE) {
            return "薪酬属于敏感信息，普通员工无法通过系统查询具体金额，请联系HR。";
        }
        BizSalary salary = salaryRepository.findByEmployeeId(user.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("暂无薪酬数据"));
        BizEmployee emp = requireEmployee(user.getEmployeeId());
        return String.format("员工: %s\n薪酬等级: %s\n基本月薪: %.0f 元\n最近调薪: %s",
                emp.getName(), salary.getSalaryBand(), salary.getBaseSalary(), salary.getLastAdjustDate());
    }

    private String queryPersonalPerformance(UserPrincipal user) {
        BizEmployee emp = requireEmployee(user.getEmployeeId());
        BizPerformance perf = performanceRepository.findFirstByEmployeeIdOrderByYearDescQuarterDesc(
                user.getEmployeeId()).orElse(null);
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工: " + emp.getName());
        if (perf != null) {
            sj.add("最近绩效: " + perf.getYear() + " " + perf.getQuarter());
            sj.add("评级: " + perf.getRating() + "，分数: " + perf.getScore());
            sj.add("可晋升: " + (Boolean.TRUE.equals(perf.getPromotionEligible()) ? "是" : "否"));
        }
        return sj.toString();
    }

    private String queryDeptOvertime(UserPrincipal user) {
        String deptId = resolveDeptId(user);
        permissionService.checkDepartmentAccess(deptId);
        String quarter = questionAnalyzer.currentQuarter();
        Double total = attendanceRepository.sumOvertimeByDeptAndQuarter(deptId, quarter);
        Double avg = attendanceRepository.avgOvertimeByDeptAndQuarter(deptId, quarter);
        Long lateTotal = attendanceRepository.sumLateCountByDeptAndQuarter(deptId, quarter);
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("部门: " + deptName + " (" + deptId + ")");
        sj.add("统计季度: " + quarter);
        sj.add("部门总加班时长: " + String.format("%.1f", total) + " 小时");
        sj.add("人均加班时长: " + String.format("%.1f", avg) + " 小时");
        sj.add("部门累计迟到次数: " + lateTotal);

        appendDeptOvertimeRanking(deptId, quarter, sj);
        return sj.toString();
    }

    private void appendDeptOvertimeRanking(String deptId, String quarter, StringJoiner sj) {
        sj.add("\n员工加班明细:");
        employeeRepository.findByDeptIdAndStatus(deptId, "ACTIVE").forEach(emp ->
                attendanceRepository.findByEmployeeIdAndQuarter(emp.getEmployeeId(), quarter)
                        .ifPresent(att -> sj.add(String.format("- %s: %.1f小时, 迟到%d次",
                                emp.getName(), att.getOvertimeHours(), att.getLateCount()))));
    }

    private String queryDeptHeadcount(UserPrincipal user) {
        String deptId = resolveDeptId(user);
        permissionService.checkDepartmentAccess(deptId);
        long count = employeeRepository.countByDeptIdAndStatus(deptId, "ACTIVE");
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("部门: " + deptName);
        sj.add("在职人数: " + count);
        sj.add("\n员工名单:");
        employeeRepository.findByDeptIdAndStatus(deptId, "ACTIVE").forEach(emp ->
                sj.add("- " + emp.getName() + " | " + emp.getPosition() + " | 入职:" + emp.getHireDate()));
        return sj.toString();
    }

    private String queryDeptTurnover(UserPrincipal user) {
        String deptId = resolveDeptId(user);
        permissionService.checkDepartmentAccess(deptId);
        List<BizTurnoverRisk> risks = turnoverRiskRepository.findByDeptAndRiskLevels(deptId, HIGH_RISK_LEVELS);
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("部门: " + deptName);
        sj.add("离职风险预警人数: " + risks.size());
        for (BizTurnoverRisk risk : risks) {
            BizEmployee emp = employeeRepository.findByEmployeeId(risk.getEmployeeId()).orElse(null);
            String name = emp != null ? emp.getName() : risk.getEmployeeId();
            sj.add(String.format("- %s | 风险分:%.0f%% | 等级:%s | 因素:%s | 建议:%s",
                    name, risk.getRiskScore() * 100, risk.getRiskLevel(),
                    risk.getFactors(), risk.getRecommendation()));
        }
        return sj.toString();
    }

    private String queryDeptPerformance(UserPrincipal user) {
        String deptId = resolveDeptId(user);
        permissionService.checkDepartmentAccess(deptId);
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("部门: " + deptName + " 绩效概况");
        employeeRepository.findByDeptIdAndStatus(deptId, "ACTIVE").forEach(emp -> {
            performanceRepository.findFirstByEmployeeIdOrderByYearDescQuarterDesc(emp.getEmployeeId())
                    .ifPresent(perf -> sj.add(String.format("- %s: 评级%s, 分数%.1f, 满意度%.1f",
                            emp.getName(), perf.getRating(), perf.getScore(), emp.getSatisfactionScore())));
        });
        return sj.toString();
    }

    private String queryDeptSalary(UserPrincipal user) {
        permissionService.checkDepartmentAccess(resolveDeptId(user));
        if (user.getRole() == UserRole.MANAGER) {
            return "部门经理无权查看薪酬明细数据，请联系HRBP或HR管理员。";
        }
        String deptId = resolveDeptId(user);
        Double avg = salaryRepository.avgBaseSalaryByDept(deptId);
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("部门: " + deptName);
        sj.add("薪酬中位数(均值): " + String.format("%.0f", avg) + " 元/月");
        sj.add("全公司均值: " + String.format("%.0f", salaryRepository.avgBaseSalary()) + " 元/月");
        return sj.toString();
    }

    private String queryCompanyOverview(UserPrincipal user) {
        if (user.getRole() == UserRole.EMPLOYEE) {
            return "无权查看公司整体HR数据。";
        }
        if (user.getRole() == UserRole.MANAGER) {
            return queryManagerDepartmentOverview(user);
        }
        String quarter = questionAnalyzer.currentQuarter();
        StringJoiner sj = new StringJoiner("\n");
        sj.add("=== 公司HR数据概览 ===");
        sj.add("统计季度: " + quarter);
        sj.add("在职总人数: " + employeeRepository.countByStatus("ACTIVE"));
        sj.add("高风险离职预警: " + turnoverRiskRepository.countByRiskLevelIn(List.of("HIGH", "CRITICAL")) + " 人");
        if (permissionService.canViewSalary()) {
            sj.add("全公司薪酬均值: " + String.format("%.0f", salaryRepository.avgBaseSalary()) + " 元/月");
        }

        sj.add("\n各部门概况:");
        departmentRepository.findAll().forEach(dept -> {
            if ("D000".equals(dept.getDeptId())) return;
            long count = employeeRepository.countByDeptIdAndStatus(dept.getDeptId(), "ACTIVE");
            Double overtime = attendanceRepository.sumOvertimeByDeptAndQuarter(dept.getDeptId(), quarter);
            sj.add(String.format("- %s: 在职%d人, Q1总加班%.1f小时",
                    dept.getDeptName(), count, overtime != null ? overtime : 0));
        });
        return sj.toString();
    }

    private String queryManagerDepartmentOverview(UserPrincipal user) {
        String deptId = resolveDeptId(user);
        permissionService.checkDepartmentAccess(deptId);
        String quarter = questionAnalyzer.currentQuarter();
        String deptName = departmentRepository.findByDeptId(deptId).map(BizDepartment::getDeptName).orElse(deptId);
        long count = employeeRepository.countByDeptIdAndStatus(deptId, "ACTIVE");
        Double overtime = attendanceRepository.sumOvertimeByDeptAndQuarter(deptId, quarter);
        List<BizTurnoverRisk> risks = turnoverRiskRepository.findByDeptAndRiskLevels(deptId, HIGH_RISK_LEVELS);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("说明: 当前角色为部门经理，仅可查看本部门数据，以下结果不代表全公司总量。");
        sj.add("部门: " + deptName + " (" + deptId + ")");
        sj.add("统计季度: " + quarter);
        sj.add("本部门在职人数: " + count);
        sj.add("本部门高风险离职预警: " + risks.size() + " 人");
        sj.add("本部门总加班时长: " + String.format("%.1f", overtime != null ? overtime : 0) + " 小时");
        return sj.toString();
    }

    private void applyManagerScopeNoteIfNeeded(HrDataContext context, String question, UserPrincipal user) {
        if (user.getRole() != UserRole.MANAGER || context.getDataText() == null || context.getDataText().isBlank()) {
            return;
        }
        if (!containsCompanyScopeKeyword(question)) {
            return;
        }
        if (context.getDataText().startsWith("说明: 当前角色为部门经理")) {
            return;
        }
        context.setDataText("说明: 当前角色为部门经理，仅可查看本部门数据，以下结果不代表全公司总量。\n"
                + context.getDataText());
    }

    private boolean containsCompanyScopeKeyword(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return question.contains("公司")
                || question.contains("全公司")
                || question.contains("整体")
                || question.contains("全部")
                || question.contains("总人数");
    }

    private BizEmployee requireEmployee(String employeeId) {
        permissionService.checkEmployeeAccess(employeeId);
        return employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("数据库中未找到员工: " + employeeId
                        + "，请先导入 scripts/mysql/hr_test_data.sql"));
    }

    private String formatEmployeeDetail(BizEmployee emp) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("员工编号: " + emp.getEmployeeId());
        sj.add("姓名: " + emp.getName());
        departmentRepository.findByDeptId(emp.getDeptId())
                .ifPresent(d -> sj.add("部门: " + d.getDeptName()));
        sj.add("岗位: " + emp.getPosition());
        sj.add("入职日期: " + emp.getHireDate());
        sj.add("学历: " + emp.getEducation());
        sj.add("满意度: " + emp.getSatisfactionScore());
        return sj.toString();
    }

    private String resolveDeptId(UserPrincipal user) {
        if (user.getRole() == UserRole.HR_ADMIN || user.getRole() == UserRole.HRBP) {
            return user.getDepartmentId() != null ? user.getDepartmentId() : "D001";
        }
        return user.getDepartmentId();
    }
}
