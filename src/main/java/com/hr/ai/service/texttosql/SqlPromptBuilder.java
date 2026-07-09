package com.hr.ai.service.texttosql;

import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.HrQuestionAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 构建 Text-to-SQL 的 System / User / Correction Prompt，含 Few-shot 示例。
 */
@Component
@RequiredArgsConstructor
public class SqlPromptBuilder {

    private final TextToSqlProperties properties;
    private final HrSchemaProvider schemaProvider;
    private final HrQuestionAnalyzer questionAnalyzer;

    private static final String SYSTEM_TEMPLATE = """
            你是 MySQL SQL 专家，负责将用户的自然语言 HR 数据问题转换为 **单条 SELECT 查询语句**。

            严格要求：
            1. 只输出一条 SELECT 语句，不要解释；可用 ```sql 代码块包裹
            2. 只能使用提供的 biz_* 表，禁止 INSERT/UPDATE/DELETE/DROP/UNION 等
            3. 必须包含 LIMIT（不超过 %d 行）
            4. 使用 MySQL 语法，表名/字段名与 Schema 完全一致
            5. 涉及员工姓名时 JOIN biz_employee 获取 name 字段
            6. 当前考勤汇总季度默认为 %s（用户未指定时使用）
            7. 聚合查询使用 GROUP BY，指标列使用中文别名（AS 部门名称、总加班时长 等）
            8. 多表关联优先：biz_employee e → 其他表 ON e.employee_id
            9. 过滤在职员工时使用 e.status = 'ACTIVE'
            10. 风险等级枚举：LOW / MEDIUM / HIGH / CRITICAL；绩效评级：A/B/C/D
            """;

    public String buildSystemPrompt(UserPrincipal user) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_TEMPLATE.formatted(properties.getMaxRows(), questionAnalyzer.currentQuarter()));
        sb.append("\n").append(buildPermissionRules(user));
        sb.append("\n\n数据库 Schema:\n").append(schemaProvider.buildSchemaDescription());
        if (properties.isFewShotEnabled()) {
            sb.append("\n\n参考示例（仅供学习 SQL 写法，需按用户权限调整 WHERE 条件）:\n");
            sb.append(schemaProvider.buildFewShotExamples());
        }
        return sb.toString();
    }

    public String buildUserPrompt(String question) {
        return "请将以下 HR 数据问题转换为 SQL：\n" + question;
    }

    public String buildCorrectionPrompt(String question, String failedSql, String errorMessage) {
        return """
                之前生成的 SQL 执行失败，请修正后重新输出 **单条 SELECT**。

                用户问题：%s

                失败的 SQL：
                %s

                数据库报错：
                %s

                请根据 Schema 和权限约束修正 SQL，只输出修正后的 SELECT 语句。
                """.formatted(question, failedSql, errorMessage);
    }

    private String buildPermissionRules(UserPrincipal user) {
        StringBuilder rules = new StringBuilder("\n权限约束（必须在 WHERE 中体现）:\n");
        rules.append("- 当前用户角色: ").append(user.getRole()).append("\n");
        rules.append("- 当前用户员工编号: ").append(user.getEmployeeId()).append("\n");
        rules.append("- 当前用户部门编码: ").append(user.getDepartmentId()).append("\n");

        switch (user.getRole()) {
            case EMPLOYEE -> rules.append("""
                    - 只能查询 employee_id = '%s' 的本人数据
                    - 禁止查询 biz_salary 表
                    - 示例: WHERE e.employee_id = '%s'
                    """.formatted(user.getEmployeeId(), user.getEmployeeId()));
            case MANAGER -> rules.append("""
                    - 只能查询 dept_id = '%s' 的本部门数据
                    - 禁止查询 biz_salary 表及 base_salary 字段
                    - 示例: WHERE e.dept_id = '%s'
                    """.formatted(user.getDepartmentId(), user.getDepartmentId()));
            case HRBP, HR_ADMIN -> rules.append("""
                    - 可查询全公司数据
                    - 可查询 biz_salary 表
                    """);
        }
        return rules.toString();
    }
}
