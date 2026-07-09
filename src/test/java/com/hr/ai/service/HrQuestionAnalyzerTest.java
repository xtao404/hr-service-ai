package com.hr.ai.service;

import com.hr.ai.config.PresetQueryProperties;
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

    private TextToSqlProperties textToSqlProperties;
    private PresetQueryProperties presetQueryProperties;

    @BeforeEach
    void setUp() {
        textToSqlProperties = new TextToSqlProperties();
        textToSqlProperties.setEnabled(true);
        presetQueryProperties = new PresetQueryProperties();
        presetQueryProperties.setEnabled(true);
    }

    @Test
    void namedEmployeeSalary_notRoutedToDeptSalary() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("查询张三的工资", manager));
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三的工资", manager));
    }

    @Test
    void namedEmployeeOvertime_notRoutedToDeptOvertime() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("赵六的加班时长", manager));
    }

    @Test
    void namedEmployeePerformance_notRoutedToDeptPerformance() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三的绩效评级", manager));
    }

    @Test
    void namedEmployeeTurnover_notRoutedToDeptTurnover() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("钱七的离职风险", manager));
    }

    @Test
    void aggregateSalaryQuery_stillRoutedToDept() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal hrbp = user(UserRole.HRBP, "E003", "D000");
        assertEquals(HrQueryIntent.DEPT_SALARY, analyzer.analyze("部门薪酬竞争力", hrbp));
        assertEquals(HrQueryIntent.DEPT_SALARY, analyzer.analyze("统计张三的薪酬", hrbp));
    }

    @Test
    void deptOvertimeQuery_notMisidentifiedAsNamedEmployee() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.DEPT_OVERTIME, analyzer.analyze("统计本季度部门加班时长", manager));
    }

    @Test
    void employeeQueryOthers_routedToNamedEmployee() {
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三的加班时长", employee));
    }

    @Test
    void extractNamedEmployeeQuery_infersTopic() {
        HrQuestionAnalyzer analyzer = analyzer();
        NamedEmployeeQuery salary = analyzer.extractNamedEmployeeQuery("张三的工资").orElseThrow();
        assertEquals("张三", salary.getEmployeeName());
        assertEquals(EmployeeQueryTopic.SALARY, salary.getTopic());

        NamedEmployeeQuery overtime = analyzer.extractNamedEmployeeQuery("赵六的加班时长").orElseThrow();
        assertEquals("赵六", overtime.getEmployeeName());
        assertEquals(EmployeeQueryTopic.OVERTIME, overtime.getTopic());
    }

    @Test
    void namedEmployeeLeave_withoutDe_orWithAskPrefix() {
        HrQuestionAnalyzer analyzer = analyzer();
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
        HrQuestionAnalyzer analyzer = analyzer();
        assertTrue(analyzer.extractNamedEmployeeQuery("统计部门加班情况").isEmpty());
        assertTrue(analyzer.extractNamedEmployeeQuery("对比各部门绩效").isEmpty());
    }

    @Test
    void presetDisabled_fallsBackToTextToSql() {
        presetQueryProperties.setEnabled(false);
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("我的加班时长", manager));
        assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("张三假期", manager));
        assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("统计本季度部门加班时长", manager));
    }

    @Test
    void presetDisabled_andTextToSqlDisabled_fallsBackToKnowledge() {
        presetQueryProperties.setEnabled(false);
        textToSqlProperties.setEnabled(false);
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("我的加班时长", manager));
    }

    @Test
    void presetDisabled_employeeQueryOthers_fallsBackToKnowledge() {
        presetQueryProperties.setEnabled(false);
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
        assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("张三的加班时长", employee));
    }

    @Test
    void presetDisabled_policyQuestion_stillKnowledge() {
        presetQueryProperties.setEnabled(false);
        HrQuestionAnalyzer analyzer = analyzer();
        UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
        assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("年假有多少天", employee));
    }

    private HrQuestionAnalyzer analyzer() {
        return new HrQuestionAnalyzer(textToSqlProperties, presetQueryProperties);
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
