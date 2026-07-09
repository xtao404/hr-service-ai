package com.hr.ai.service.texttosql;

import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.model.entity.User;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSecurityValidatorTest {

    private SqlSecurityValidator validator;
    private UserPrincipal manager;

    @BeforeEach
    void setUp() {
        TextToSqlProperties properties = new TextToSqlProperties();
        properties.setMaxRows(100);
        validator = new SqlSecurityValidator(properties, new SqlExtractor(), new SqlAliasQuoter());
        manager = user(UserRole.MANAGER, "M001", "D001");
    }

    @Test
    void validSelect_passesValidation() {
        String sql = "SELECT e.name, a.overtime_hours FROM biz_employee e "
                + "JOIN biz_attendance a ON e.employee_id = a.employee_id LIMIT 10";
        String sanitized = validator.validateAndSanitize(sql, manager, false);
        assertTrue(sanitized.toUpperCase().contains("LIMIT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "'; DROP TABLE biz_employee; --",
            "SELECT * FROM users",
            "SELECT e.name FROM biz_employee e UNION SELECT name FROM biz_employee",
            "SELECT 1; SELECT 2 FROM biz_employee",
            "SELECT name FROM biz_employee -- comment"
    })
    @DisplayName("SQL-14~18 安全拦截")
    void dangerousSql_rejected(String sql) {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndSanitize(sql, manager, false));
    }

    @Test
    void managerWithoutSalaryPermission_rejectsSalaryTable() {
        String sql = "SELECT AVG(base_salary) FROM biz_salary LIMIT 10";
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndSanitize(sql, manager, false));
    }

    @Test
    void hrbpWithSalaryPermission_allowsSalaryTable() {
        UserPrincipal hrbp = user(UserRole.HRBP, "E003", "D000");
        String sql = "SELECT AVG(base_salary) FROM biz_salary LIMIT 10";
        assertDoesNotThrow(() -> validator.validateAndSanitize(sql, hrbp, true));
    }

    @Test
    void limitOverMax_cappedTo100() {
        String sql = "SELECT name FROM biz_employee LIMIT 500";
        String sanitized = validator.validateAndSanitize(sql, manager, false);
        assertTrue(sanitized.contains("LIMIT 100"));
    }

    @Test
    void missingLimit_appendedAutomatically() {
        String sql = "SELECT name FROM biz_employee";
        String sanitized = validator.validateAndSanitize(sql, manager, false);
        assertTrue(sanitized.endsWith("LIMIT 100"));
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
