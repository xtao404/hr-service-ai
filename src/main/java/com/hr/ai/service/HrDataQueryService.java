package com.hr.ai.service;

import com.hr.ai.dto.EmployeeProfileResponse;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.model.entity.biz.*;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.repository.biz.*;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.texttosql.TextToSqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public HrQueryIntent detectIntent(String question, UserPrincipal user) {
        return questionAnalyzer.analyze(question, user);
    }

    public boolean isDatabaseIntent(HrQueryIntent intent) {
        return intent != HrQueryIntent.KNOWLEDGE;
    }

    public HrDataContext query(String question, UserPrincipal user) {
        HrQueryIntent intent = questionAnalyzer.analyze(question, user);

        if (intent == HrQueryIntent.TEXT_TO_SQL) {
            return textToSqlService.query(question, user);
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
            case DEPT_OVERTIME -> fillDeptOvertime(context, user);
            case DEPT_HEADCOUNT -> fillDeptHeadcount(context, user);
            case DEPT_TURNOVER -> fillDeptTurnover(context, user);
            case DEPT_PERFORMANCE -> fillDeptPerformance(context, user);
            case DEPT_SALARY -> fillDeptSalary(context, user);
            case COMPANY_OVERVIEW -> fillCompanyOverview(context, user);
            default -> context.setDataText("");
        }
        return context;
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
        permissionService.checkEmployeeAccess(employeeId);
        BizEmployee emp = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("未找到员工数据: " + employeeId));

        EmployeeProfileResponse profile = new EmployeeProfileResponse();
        profile.setEmployeeId(emp.getEmployeeId());
        profile.setName(emp.getName());
        profile.setPosition(emp.getPosition());

        departmentRepository.findByDeptId(emp.getDeptId())
                .ifPresent(dept -> profile.setDepartmentName(dept.getDeptName()));

        attendanceRepository.findByEmployeeIdAndQuarter(employeeId, questionAnalyzer.currentQuarter())
                .ifPresent(att -> {
                    profile.setLeaveBalance(att.getLeaveBalance());
                    profile.setOvertimeHours(att.getOvertimeHours());
                });

        salaryRepository.findByEmployeeId(employeeId)
                .ifPresent(sal -> profile.setSalaryBand(sal.getSalaryBand()));

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
        String quarter = questionAnalyzer.currentQuarter();
        StringJoiner sj = new StringJoiner("\n");
        sj.add("=== 公司HR数据概览 ===");
        sj.add("统计季度: " + quarter);
        sj.add("在职总人数: " + employeeRepository.countByStatus("ACTIVE"));
        sj.add("高风险离职预警: " + turnoverRiskRepository.countByRiskLevelIn(List.of("HIGH", "CRITICAL")) + " 人");
        sj.add("全公司薪酬均值: " + String.format("%.0f", salaryRepository.avgBaseSalary()) + " 元/月");

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
