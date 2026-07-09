package com.hr.ai.service;

import com.hr.ai.dto.EmployeeProfileResponse;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.dto.ManagerReportResponse;
import com.hr.ai.dto.ReportMetric;
import com.hr.ai.dto.SourceReference;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HrIntegrationService {

    private final PermissionService permissionService;
    private final LlmService llmService;
    private final HrDataQueryService hrDataQueryService;

    @Value("${hr.ai.integration.enabled:false}")
    private boolean integrationEnabled;

    public EmployeeProfileResponse getEmployeeProfile(String employeeId) {
        return hrDataQueryService.getEmployeeProfile(employeeId);
    }

    public ManagerReportResponse generateReport(String query, String period, String departmentId) {
        UserPrincipal user = permissionService.currentUser();
        String deptId = departmentId != null ? departmentId : user.getDepartmentId();
        permissionService.checkDepartmentAccess(deptId);

        String deptName = user.getDepartmentName() != null ? user.getDepartmentName() : "技术研发部";
        String reportPeriod = period != null ? period : "上季度";

        HrDataContext dataContext = hrDataQueryService.query(query, user);
        List<ReportMetric> metrics = parseMetricsFromData(dataContext.getDataText(), query);

        if (metrics.isEmpty()) {
            metrics = buildFallbackMetrics(query);
        }

        String detail = llmService.generateReport(query, dataContext.getDataText());

        ManagerReportResponse response = new ManagerReportResponse();
        response.setReportTitle(deptName + " - " + reportPeriod + " HR分析报告");
        response.setSummary("已完成「" + query + "」的数据库查询与分析，共涉及 " + metrics.size() + " 项核心指标。");
        response.setDetail(detail);
        response.setMetrics(metrics);

        SourceReference source = new SourceReference();
        source.setTitle(integrationEnabled ? "HR系统实时数据" : "HR业务数据库");
        source.setSnippet("数据来源: MySQL biz_* 业务表");
        source.setRelevance(1.0);
        response.setDataSources(List.of(source));

        return response;
    }

    private List<ReportMetric> parseMetricsFromData(String dataText, String query) {
        List<ReportMetric> metrics = new ArrayList<>();
        if (dataText == null) return metrics;

        addMetricIfFound(metrics, dataText, "部门总加班时长", "小时");
        addMetricIfFound(metrics, dataText, "人均加班时长", "小时");
        addMetricIfFound(metrics, dataText, "在职人数", "人");
        addMetricIfFound(metrics, dataText, "在职总人数", "人");
        addMetricIfFound(metrics, dataText, "离职风险预警人数", "人");
        addMetricIfFound(metrics, dataText, "高风险离职预警", "人");
        addMetricIfFound(metrics, dataText, "全公司薪酬均值", "元/月");
        addMetricIfFound(metrics, dataText, "薪酬中位数(均值)", "元/月");

        return metrics;
    }

    private void addMetricIfFound(List<ReportMetric> metrics, String dataText, String name, String unit) {
        Pattern pattern = Pattern.compile(Pattern.quote(name) + "[:：]\\s*([\\d.,]+)");
        Matcher matcher = pattern.matcher(dataText);
        if (matcher.find()) {
            ReportMetric m = new ReportMetric();
            m.setName(name);
            m.setValue(matcher.group(1));
            m.setUnit(unit);
            m.setTrend("→");
            metrics.add(m);
        }
    }

    private List<ReportMetric> buildFallbackMetrics(String query) {
        List<ReportMetric> metrics = new ArrayList<>();
        if (query.contains("加班")) {
            metrics.add(metric("部门总加班时长", "-", "小时", "→"));
            metrics.add(metric("人均加班时长", "-", "小时", "→"));
        } else {
            metrics.add(metric("在职人数", "-", "人", "→"));
        }
        return metrics;
    }

    public Map<String, Object> fetchDepartmentStats(String departmentId) {
        permissionService.checkDepartmentAccess(departmentId);
        HrDataContext ctx = hrDataQueryService.query("部门统计", permissionService.currentUser());
        return Map.of("rawData", ctx.getDataText());
    }

    private ReportMetric metric(String name, String value, String unit, String trend) {
        ReportMetric m = new ReportMetric();
        m.setName(name);
        m.setValue(value);
        m.setUnit(unit);
        m.setTrend(trend);
        return m;
    }
}
