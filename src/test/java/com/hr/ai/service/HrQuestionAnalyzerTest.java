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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        presetQueryProperties.setEnabled(false);
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

    @Nested
    @DisplayName("默认配置 preset=false, text-to-sql=true")
    class DefaultConfiguration {

        @Test
        void personalQueries_routeToTextToSql() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("我的加班时长", employee));
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("我的假期余额是多少", employee));
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("我的绩效评级", employee));
        }

        @Test
        void deptQueries_routeToTextToSql() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("统计本季度部门加班时长", manager));
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("部门在职人数多少", manager));
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("哪些员工有离职风险", manager));
        }

        @Test
        void benefitListing_withListKeyword_staysKnowledge() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("列出福利类型", manager));
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("找出公司有哪些福利", manager));
        }

        @Test
        void namedEmployeeQuery_routeToTextToSql() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("张三假期", manager));
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("赵六的加班时长", manager));
        }

        @Test
        void employeeQueryOthers_fallsBackToKnowledge() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("张三的加班时长", employee));
        }

        @Test
        void policyQuestion_staysKnowledge() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("年假有多少天", employee));
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("列出公司福利类型", employee));
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("五险一金包括哪些", employee));
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("公司调薪政策是什么", employee));
        }

        @Test
        void employeeCompanyStats_fallsBackToKnowledge() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("统计公司加班情况", employee));
        }

        @Test
        void bothDisabled_fallsBackToKnowledge() {
            textToSqlProperties.setEnabled(false);
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("我的加班时长", manager));
        }
    }

    @Nested
    @DisplayName("Text-to-SQL 复杂分析 gate")
    class TextToSqlGate {

        @BeforeEach
        void enablePresetForIsolation() {
            presetQueryProperties.setEnabled(true);
        }

        @ParameterizedTest(name = "「{0}」→ TEXT_TO_SQL")
        @CsvSource({
                "对比各部门加班时长",
                "找出满意度低于7分的员工",
                "查询绩效为C且加班超过50小时的员工",
                "各部门平均薪酬",
                "按部门排序加班时长"
        })
        void complexKeywords_routeToTextToSqlFirst(String question) {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze(question, manager));
        }

        @Test
        void employeeNonPersonal_blockedFromTextToSql() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.KNOWLEDGE, analyzer.analyze("对比各部门加班时长", employee));
        }

        @Test
        void employeePersonalRanking_allowedTextToSql() {
            presetQueryProperties.setEnabled(false);
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.TEXT_TO_SQL, analyzer.analyze("我的加班时长排名", employee));
        }
    }

    @Nested
    @DisplayName("preset 启用 preset=true")
    class PresetQueryEnabled {

        @BeforeEach
        void enablePreset() {
            presetQueryProperties.setEnabled(true);
        }

        @Test
        void personalQueries_routeToPreset() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal employee = user(UserRole.EMPLOYEE, "E001", "D001");
            assertEquals(HrQueryIntent.PERSONAL_OVERTIME, analyzer.analyze("我的加班时长", employee));
            assertEquals(HrQueryIntent.PERSONAL_LEAVE, analyzer.analyze("我的假期余额是多少", employee));
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
        void namedEmployeeLeave_withoutDe_orWithAskPrefix() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
            assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("张三假期", manager));
            assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("问张三的假期", manager));
            assertEquals(HrQueryIntent.NAMED_EMPLOYEE, analyzer.analyze("查张三假期余额", manager));
        }

        @Test
        void companyOverview_forHrbp() {
            HrQuestionAnalyzer analyzer = analyzer();
            UserPrincipal hrbp = user(UserRole.HRBP, "E003", "D000");
            assertEquals(HrQueryIntent.COMPANY_OVERVIEW, analyzer.analyze("公司HR整体概览", hrbp));
        }
    }

    @Nested
    @DisplayName("指定员工姓名与主题提取")
    class NamedEmployeeExtraction {

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
        void extractLeaveTopic_fromDirectPattern() {
            HrQuestionAnalyzer analyzer = analyzer();
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

        @ParameterizedTest
        @CsvSource({
                "赵六的满意度, SATISFACTION",
                "张三的考勤记录, ATTENDANCE",
                "钱七的档案, PROFILE"
        })
        void extractNamedEmployeeQuery_allTopics(String question, EmployeeQueryTopic topic) {
            HrQuestionAnalyzer analyzer = analyzer();
            NamedEmployeeQuery query = analyzer.extractNamedEmployeeQuery(question).orElseThrow();
            assertEquals(topic, query.getTopic());
        }
    }
}
