package com.hr.ai.service.texttosql;

import com.hr.ai.config.LlmProperties;
import com.hr.ai.config.PresetQueryProperties;
import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.model.entity.User;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.HrQuestionAnalyzer;
import com.hr.ai.service.QwenChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TextToSqlMockSqlTest {

    @Mock
    private QwenChatClient qwenChatClient;
    @Mock
    private SqlPromptBuilder sqlPromptBuilder;
    @Mock
    private SqlSecurityValidator sqlSecurityValidator;
    @Mock
    private SqlPermissionRewriter sqlPermissionRewriter;
    @Mock
    private SqlExecutionService sqlExecutionService;
    @Mock
    private PermissionService permissionService;

    private TextToSqlService textToSqlService;
    private HrQuestionAnalyzer questionAnalyzer;

    @BeforeEach
    void setUp() {
        TextToSqlProperties textToSqlProperties = new TextToSqlProperties();
        textToSqlProperties.setEnabled(true);
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setProvider("mock");
        questionAnalyzer = new HrQuestionAnalyzer(textToSqlProperties, new PresetQueryProperties());

        textToSqlService = new TextToSqlService(
                textToSqlProperties,
                llmProperties,
                qwenChatClient,
                sqlPromptBuilder,
                sqlSecurityValidator,
                sqlPermissionRewriter,
                sqlExecutionService,
                permissionService,
                questionAnalyzer
        );
    }

    @Test
    void mockSql_personalLeave_containsLeaveBalance() throws Exception {
        String sql = invokeGenerateMockSql("我的假期余额是多少？", user(UserRole.EMPLOYEE, "E001", "D001"));
        assertTrue(sql.contains("leave_balance"));
        assertTrue(sql.contains("E001"));
    }

    @Test
    void mockSql_deptOvertime_containsEmployeeOvertime() throws Exception {
        String sql = invokeGenerateMockSql("统计本季度部门加班时长", user(UserRole.MANAGER, "M001", "D001"));
        assertTrue(sql.contains("overtime_hours"));
        assertTrue(sql.contains("D001"));
    }

    @Test
    void mockSql_namedEmployeeOvertime_filtersByName() throws Exception {
        String sql = invokeGenerateMockSql("赵六的加班时长", user(UserRole.MANAGER, "M001", "D001"));
        assertTrue(sql.contains("赵六"));
        assertTrue(sql.contains("overtime_hours"));
    }

    @Test
    void mockSql_hrbpPersonalSalary_queriesSalaryTable() throws Exception {
        String sql = invokeGenerateMockSql("我的薪酬是多少？", user(UserRole.HRBP, "E003", "D000"));
        assertTrue(sql.contains("base_salary"));
        assertTrue(sql.contains("E003"));
    }

    private String invokeGenerateMockSql(String question, UserPrincipal user) throws Exception {
        Method method = TextToSqlService.class.getDeclaredMethod("generateMockSql", String.class, UserPrincipal.class);
        method.setAccessible(true);
        return (String) method.invoke(textToSqlService, question, user);
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
