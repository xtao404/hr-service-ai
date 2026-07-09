package com.hr.ai.service.texttosql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAliasQuoterTest {

    private final SqlAliasQuoter quoter = new SqlAliasQuoter();

    @Test
    void quotesAliasesWithHyphenOrLeadingDigit() {
        String sql = "SELECT a.overtime_hours AS 2026-Q1加班时长, p.rating AS 2026年绩效评级, "
                + "e.name AS 姓名 FROM biz_employee e LIMIT 1";

        String fixed = quoter.quoteUnsafeAliases(sql);

        assertTrue(fixed.contains("AS `2026-Q1加班时长`"));
        assertTrue(fixed.contains("AS `2026年绩效评级`"));
        assertTrue(fixed.contains("AS 姓名"));
        assertFalse(fixed.contains("AS 2026-Q1"));
    }

    @Test
    void quotesOrderByReferencesForUnsafeAliases() {
        String sql = "SELECT d.dept_name AS 部门, SUM(a.overtime_hours) AS 2026-Q1总加班 "
                + "FROM biz_department d GROUP BY d.dept_name ORDER BY 2026-Q1总加班 DESC LIMIT 10";

        String fixed = quoter.quoteUnsafeAliases(sql);

        assertTrue(fixed.contains("AS `2026-Q1总加班`"));
        assertTrue(fixed.contains("ORDER BY `2026-Q1总加班` DESC"));
    }
}
