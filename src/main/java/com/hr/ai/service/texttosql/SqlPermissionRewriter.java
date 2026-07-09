package com.hr.ai.service.texttosql;

import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在 LLM 生成的 SQL 上强制注入角色级数据隔离条件。
 * 作为 {@link TextToSqlService#enforcePermissionFilter} 的补充，降低 LLM 遗漏 WHERE 的风险。
 */
@Component
public class SqlPermissionRewriter {

    private static final Pattern CLAUSE_SPLIT = Pattern.compile(
            "\\b(GROUP\\s+BY|ORDER\\s+BY|HAVING|LIMIT)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 若 SQL 未包含必要的权限过滤条件，则在 GROUP BY / ORDER BY / LIMIT 之前注入 AND 条件。
     */
    public String injectPermissionFilter(String sql, UserPrincipal user) {
        if (user.getRole() == UserRole.HRBP || user.getRole() == UserRole.HR_ADMIN) {
            return sql;
        }

        String upper = sql.toUpperCase(Locale.ROOT);
        String filter = buildFilterClause(user, upper);

        if (filter == null || alreadyContainsFilter(sql, user)) {
            return sql;
        }

        Matcher matcher = CLAUSE_SPLIT.matcher(sql);
        if (matcher.find()) {
            int insertPos = matcher.start();
            return sql.substring(0, insertPos).trim() + " " + filter + " "
                    + sql.substring(insertPos).trim();
        }

        // 无 GROUP/ORDER/LIMIT，直接追加到末尾
        if (upper.contains(" WHERE ")) {
            return sql.trim() + " " + filter;
        }
        return sql.trim() + " WHERE 1=1 " + filter;
    }

    private String buildFilterClause(UserPrincipal user, String upperSql) {
        String employeeAlias = detectEmployeeAlias(upperSql);
        return switch (user.getRole()) {
            case EMPLOYEE -> "AND " + employeeAlias + ".employee_id = '" + user.getEmployeeId() + "'";
            case MANAGER -> "AND " + employeeAlias + ".dept_id = '" + user.getDepartmentId() + "'";
            default -> null;
        };
    }

    private String detectEmployeeAlias(String upperSql) {
        // JOIN biz_employee e / JOIN biz_employee AS e
        Matcher aliasMatcher = Pattern.compile(
                "JOIN\\s+BIZ_EMPLOYEE\\s+(?:AS\\s+)?(\\w+)",
                Pattern.CASE_INSENSITIVE).matcher(upperSql);
        if (aliasMatcher.find()) {
            return aliasMatcher.group(1).toLowerCase(Locale.ROOT);
        }
        if (upperSql.contains("BIZ_EMPLOYEE")) {
            return "biz_employee";
        }
        // 考勤/绩效等表通常通过 employee_id 关联
        return "biz_employee";
    }

    private boolean alreadyContainsFilter(String sql, UserPrincipal user) {
        return switch (user.getRole()) {
            case EMPLOYEE -> sql.contains("'" + user.getEmployeeId() + "'")
                    || sql.contains("\"" + user.getEmployeeId() + "\"");
            case MANAGER -> sql.contains("'" + user.getDepartmentId() + "'")
                    || sql.contains("\"" + user.getDepartmentId() + "\"");
            default -> true;
        };
    }
}
