package com.hr.ai.service.texttosql;

import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 安全校验：仅允许 SELECT、白名单表、禁止危险关键字、强制 LIMIT。
 */
@Component
@RequiredArgsConstructor
public class SqlSecurityValidator {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER", "CREATE",
            "REPLACE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "MERGE", "CALL",
            "INTO", "OUTFILE", "DUMPFILE", "LOAD", "HANDLER", "LOCK", "UNION",
            "INFORMATION_SCHEMA", "MYSQL", "PERFORMANCE_SCHEMA", "SYS."
    );

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "biz_department", "biz_employee", "biz_attendance",
            "biz_salary", "biz_performance", "biz_turnover_risk"
    );

    private static final Pattern MULTI_STATEMENT = Pattern.compile(";\\s*\\S");
    private static final Pattern LIMIT_VALUE = Pattern.compile(
            "\\bLIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final TextToSqlProperties properties;
    private final SqlExtractor sqlExtractor;
    private final SqlAliasQuoter sqlAliasQuoter;

    public String validateAndSanitize(String rawSql, UserPrincipal user, boolean canViewSalary) {
        String sql = sqlExtractor.extract(rawSql);
        sql = sqlAliasQuoter.quoteUnsafeAliases(sql);
        String upper = sql.toUpperCase(Locale.ROOT);

        if (!upper.startsWith("SELECT")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询");
        }
        if (MULTI_STATEMENT.matcher(sql).find()) {
            throw new IllegalArgumentException("不允许执行多条 SQL 语句");
        }
        if (upper.contains("--") || upper.contains("/*")) {
            throw new IllegalArgumentException("SQL 不允许包含注释");
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (containsKeyword(upper, keyword)) {
                throw new IllegalArgumentException("SQL 包含禁止的操作: " + keyword);
            }
        }

        validateTables(upper);

        if (!canViewSalary && upper.contains("BIZ_SALARY")) {
            throw new IllegalArgumentException("您无权查询薪酬表数据");
        }

        sql = capLimit(sql);
        return sql;
    }

    private void validateTables(String upperSql) {
        boolean hasAllowed = false;
        for (String table : ALLOWED_TABLES) {
            if (upperSql.contains(table.toUpperCase(Locale.ROOT))) {
                hasAllowed = true;
                break;
            }
        }
        if (!hasAllowed) {
            throw new IllegalArgumentException("SQL 必须查询 HR 业务表（biz_*）");
        }
    }

    private boolean containsKeyword(String upperSql, String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(upperSql).find();
    }

    /**
     * 确保有 LIMIT，且不超过配置上限。
     */
    private String capLimit(String sql) {
        Matcher matcher = LIMIT_VALUE.matcher(sql);
        if (matcher.find()) {
            int limit = Integer.parseInt(matcher.group(1));
            if (limit > properties.getMaxRows()) {
                return sql.substring(0, matcher.start())
                        + "LIMIT " + properties.getMaxRows()
                        + sql.substring(matcher.end());
            }
            return sql;
        }
        return sql + " LIMIT " + properties.getMaxRows();
    }
}
