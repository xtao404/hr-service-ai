package com.hr.ai.service;

import com.hr.ai.dto.EmployeeRef;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.dto.QueryTrace;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.repository.biz.BizEmployeeRepository;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class QueryTraceBuilder {

    private static final Map<HrQueryIntent, String> INTENT_LABELS = Map.ofEntries(
            Map.entry(HrQueryIntent.KNOWLEDGE, "制度知识库检索"),
            Map.entry(HrQueryIntent.PERSONAL_PROFILE, "个人档案查询"),
            Map.entry(HrQueryIntent.PERSONAL_LEAVE, "个人假期查询"),
            Map.entry(HrQueryIntent.PERSONAL_OVERTIME, "个人加班查询"),
            Map.entry(HrQueryIntent.PERSONAL_ATTENDANCE, "个人考勤查询"),
            Map.entry(HrQueryIntent.PERSONAL_SALARY, "个人薪酬查询"),
            Map.entry(HrQueryIntent.PERSONAL_PERFORMANCE, "个人绩效查询"),
            Map.entry(HrQueryIntent.DEPT_OVERTIME, "部门加班统计"),
            Map.entry(HrQueryIntent.DEPT_HEADCOUNT, "部门人数统计"),
            Map.entry(HrQueryIntent.DEPT_TURNOVER, "部门离职风险"),
            Map.entry(HrQueryIntent.DEPT_PERFORMANCE, "部门绩效分析"),
            Map.entry(HrQueryIntent.DEPT_SALARY, "部门薪酬分析"),
            Map.entry(HrQueryIntent.COMPANY_OVERVIEW, "公司 HR 概览"),
            Map.entry(HrQueryIntent.TEXT_TO_SQL, "智能 SQL 分析")
    );

    private final BizEmployeeRepository employeeRepository;

    public QueryTrace buildKnowledgeTrace() {
        QueryTrace trace = new QueryTrace();
        trace.setRouteType("KNOWLEDGE");
        trace.setIntent(HrQueryIntent.KNOWLEDGE.name());
        trace.setIntentLabel(INTENT_LABELS.get(HrQueryIntent.KNOWLEDGE));
        trace.setDataSource("知识库 knowledge_documents");
        trace.setQueryMethod("rag");
        trace.setPermissionNote("已登录用户均可检索制度文档");
        return trace;
    }

    public QueryTrace buildDataTrace(HrDataContext context, UserPrincipal user) {
        QueryTrace trace = new QueryTrace();
        HrQueryIntent intent = context.getIntent() != null ? context.getIntent() : HrQueryIntent.KNOWLEDGE;
        boolean textToSql = "text-to-sql".equals(context.getQueryMethod());

        trace.setRouteType(textToSql ? "TEXT_TO_SQL" : "PRESET_QUERY");
        trace.setIntent(intent.name());
        trace.setIntentLabel(INTENT_LABELS.getOrDefault(intent, intent.name()));
        trace.setDataSource(context.getDataSource());
        trace.setQueryMethod(context.getQueryMethod());
        trace.setRowCount(context.getRowCount() != null
                ? context.getRowCount()
                : (context.getQueryRows() != null ? context.getQueryRows().size() : null));
        trace.setPermissionNote(buildPermissionNote(user));
        trace.setEmployees(extractEmployees(context.getQueryRows()));
        return trace;
    }

    private String buildPermissionNote(UserPrincipal user) {
        return switch (user.getRole()) {
            case EMPLOYEE -> "当前角色：员工，仅可查询本人（" + user.getEmployeeId() + "）相关数据";
            case MANAGER -> "当前角色：部门经理，仅可查询本部门（"
                    + (user.getDepartmentName() != null ? user.getDepartmentName() : user.getDepartmentId())
                    + "）数据，薪酬明细不可见";
            case HRBP -> "当前角色：HRBP，可查询全公司 HR 业务数据（含薪酬）";
            case HR_ADMIN -> "当前角色：HR 管理员，可查询全公司数据并管理知识库";
        };
    }

    private List<EmployeeRef> extractEmployees(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, EmployeeRef> refs = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String employeeId = findStringValue(row, "employee_id", "员工编号", "employeeId");
            String name = findStringValue(row, "name", "姓名", "employeeName");
            if (name == null && employeeId != null) {
                employeeRepository.findByEmployeeId(employeeId)
                        .ifPresent(emp -> refs.putIfAbsent(emp.getEmployeeId(), toRef(emp.getEmployeeId(), emp.getName())));
                continue;
            }
            if (name != null) {
                String key = employeeId != null ? employeeId : name;
                if (!refs.containsKey(key)) {
                    EmployeeRef ref = new EmployeeRef();
                    ref.setName(name);
                    if (employeeId != null) {
                        ref.setEmployeeId(employeeId);
                    } else {
                        employeeRepository.findByName(name).stream().findFirst()
                                .ifPresent(emp -> ref.setEmployeeId(emp.getEmployeeId()));
                    }
                    refs.put(key, ref);
                }
            }
        }
        return new ArrayList<>(refs.values());
    }

    private EmployeeRef toRef(String employeeId, String name) {
        EmployeeRef ref = new EmployeeRef();
        ref.setEmployeeId(employeeId);
        ref.setName(name);
        return ref;
    }

    private String findStringValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)
                        || entry.getKey().contains(key)
                        || key.contains(entry.getKey())) {
                    Object val = entry.getValue();
                    if (val != null && !val.toString().isBlank()) {
                        return val.toString();
                    }
                }
            }
        }
        return null;
    }
}
