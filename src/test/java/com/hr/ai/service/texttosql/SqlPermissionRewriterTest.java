package com.hr.ai.service.texttosql;

import com.hr.ai.model.entity.User;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlPermissionRewriterTest {

    private final SqlPermissionRewriter rewriter = new SqlPermissionRewriter();

    @Test
    void injectsDeptFilter_usingFromClauseAlias() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        String sql = "SELECT e.name FROM biz_employee e WHERE e.status = 'ACTIVE' LIMIT 10";

        String rewritten = rewriter.injectPermissionFilter(sql, manager);

        assertTrue(rewritten.contains("e.dept_id = 'D001'"));
    }

    @Test
    void injectsDeptFilter_beforeGroupBy() {
        UserPrincipal manager = user(UserRole.MANAGER, "M001", "D001");
        String sql = "SELECT d.dept_name, COUNT(e.id) AS cnt FROM biz_department d "
                + "JOIN biz_employee e ON d.dept_id = e.dept_id "
                + "GROUP BY d.dept_name LIMIT 10";

        String rewritten = rewriter.injectPermissionFilter(sql, manager);

        assertTrue(rewritten.contains("e.dept_id = 'D001'"));
        assertTrue(rewritten.indexOf("e.dept_id = 'D001'") < rewritten.indexOf("GROUP BY"));
    }

    @Test
    void hrbpSql_notModified() {
        UserPrincipal hrbp = user(UserRole.HRBP, "E003", "D000");
        String sql = "SELECT name FROM biz_employee LIMIT 10";
        assertTrue(rewriter.injectPermissionFilter(sql, hrbp).equalsIgnoreCase(sql));
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
