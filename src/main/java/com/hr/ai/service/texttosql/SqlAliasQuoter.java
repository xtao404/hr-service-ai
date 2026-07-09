package com.hr.ai.service.texttosql;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为含连字符、数字开头等不安全别名的 AS / ORDER BY / GROUP BY 片段补充反引号，
 * 避免 MySQL 将 2026-Q1 解析为减法运算。
 */
@Component
public class SqlAliasQuoter {

    private static final Pattern AS_ALIAS = Pattern.compile(
            "\\bAS\\s+([^'\"`,\\s][^,]*?)(?=\\s*,|\\s+FROM|\\s+WHERE|\\s+GROUP|\\s+ORDER|\\s+HAVING|\\s+(?:LEFT|RIGHT|INNER|CROSS|JOIN)\\b|\\s+LIMIT\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public String quoteUnsafeAliases(String sql) {
        Set<String> quotedAliases = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        Matcher matcher = AS_ALIAS.matcher(sql);
        int lastEnd = 0;

        while (matcher.find()) {
            sb.append(sql, lastEnd, matcher.start());
            String alias = matcher.group(1).trim();
            if (needsQuoting(alias)) {
                quotedAliases.add(alias);
                sb.append("AS `").append(escapeBacktick(alias)).append('`');
            } else {
                sb.append(matcher.group());
            }
            lastEnd = matcher.end();
        }
        sb.append(sql.substring(lastEnd));

        String result = sb.toString();
        for (String alias : quotedAliases) {
            result = quoteOrderByAndGroupBy(result, alias);
        }
        return result;
    }

    private boolean needsQuoting(String alias) {
        if (alias.isEmpty()) {
            return false;
        }
        if (alias.contains("-") || Character.isDigit(alias.charAt(0))) {
            return true;
        }
        return alias.indexOf(' ') >= 0;
    }

    private String escapeBacktick(String value) {
        return value.replace("`", "``");
    }

    private String quoteOrderByAndGroupBy(String sql, String alias) {
        String quoted = "`" + escapeBacktick(alias) + "`";
        String escaped = Pattern.quote(alias);

        sql = sql.replaceAll("(?i)(\\bORDER\\s+BY\\s+)" + escaped + "\\b", "$1" + quoted);
        sql = sql.replaceAll("(?i)(\\bGROUP\\s+BY\\s+(?:[^,]+,\\s*)*)" + escaped + "\\b", "$1" + quoted);
        return sql;
    }
}
