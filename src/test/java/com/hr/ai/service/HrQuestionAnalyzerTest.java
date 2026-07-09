package com.hr.ai.service;

import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.entity.User;
import com.hr.ai.model.enums.EmployeeQueryTopic;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrQuestionAnalyzerTest {

    private HrQuestionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        TextToSqlProperties props = new TextToSqlProperties();
        props.setEnabled(true);
        analyzer = new HrQuestionAnalyzer(props);
    }

    @Test
    void namedEmployeeSalary_notRoutedToDeptSalary() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("查询张三的工资", manager));
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三的工资", manager));
    }

    @Test
    void namedEmployeeOvertime_notRoutedToDeptOvertime() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("赵六的加班时长", manager));
    }

    @Test
    void namedEmployeePerformance_notRoutedToDeptPerformance() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三的绩效评级", manager));
    }

    @Test
    void namedEmployeeTurnover_notRoutedToDeptTurnover() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("钱七的离职风险", manager));
    }

    @Test
    void aggregateSalaryQuery_stillRoutedToDept() {
        UserPrincipal hrbp = user(UserRole.HRBP, "E003", "D000");
        assertEquals(HrQueryIntent.DEPT_SALARY, analyzer.analyze("部门薪酬竞争力", hrbp));
        assertEquals(HrQueryIntent.DEPT_SALARY, analyzer.analyze("统计张三的薪酬", hrbp));
    }

    @Test
    void deptOvertimeQuery_notMisidentifiedAsNamedEmployee() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.DEPT_OVERTIME, analyzer.analyze("统计本季度部门加班时长", manager));
    }

    @Test
    void employeeQueryOthers_routedToNamedEmployee() {
        UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三的加班时长", employee));
    }

    @Test
    void extractNamedEmployeeQuery_infersTopic() {
        NamedEmployeeQuery salary = analyzer.extractNamedEmployeeQuery("张三的工资").orElseThrow();
        assertEquals("张三", salary.getEmployeeName());
        assertEquals(EmployeeQueryTopic.SALARY, salary.getTopic());

        NamedEmployeeQuery overtime = analyzer.extractNamedEmployeeQuery("赵六的加班时长").orElseThrow();
        assertEquals("赵六", overtime.getEmployeeName());
        assertEquals(EmployeeQueryTopic.OVERTIME, overtime.getTopic());
    }

    @Test
    void namedEmployeeLeave_withoutDe_orWithAskPrefix() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三假期", manager));
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("问张三的假期", manager));
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("查张三假期余额", manager));

        NamedEmployeeQuery leave = analyzer.extractNamedEmployeeQuery("张三假期").orElseThrow();
        assertEquals("张三", leave.getEmployeeName());
        assertEquals(EmployeeQueryTopic.LEAVE, leave.getTopic());
    }

    @Test
    void aggregateQuery_doesNotExtractNamedEmployee() {
        assertTrue(analyzer.extractNamedEmployeeQuery("统计部门加班情况").isEmpty());
        assertTrue(analyzer.extractNamedEmployeeQuery("对比各部门绩效").isEmpty());
    }

    private static UserPrincipal user(UserRole role, String employeeId, String deptId) {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("pwd");
        user.setRole(role);
        user.setEmployeeId(employeeId);
        user.setDepartmentId(deptId);
        return new UserPrincipal(user);
    }
}
