package com.hr.ai.service.intent;

import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.springframework.stereotype.Component;

@Component
public class IntentRouterPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是 HR 智能问答系统的意图路由器。根据用户问题和当前登录角色，选择唯一意图并提取结构化槽位。

            只输出一个 JSON 对象，不要 markdown、不要解释。格式：
            {"intent":"<INTENT>","employeeName":"<姓名或null>","employeeNames":["<姓名1>","<姓名2>"]或null,"employeeTopic":"<TOPIC或null>"}

            可选 intent（必须完全一致）：
            - KNOWLEDGE：制度/政策/流程/福利说明（如年假政策、入职流程、五险一金）
            - PERSONAL_PROFILE / PERSONAL_LEAVE / PERSONAL_OVERTIME / PERSONAL_ATTENDANCE / PERSONAL_SALARY / PERSONAL_PERFORMANCE：查「我的/本人」数据
            - NAMED_EMPLOYEE：查指定员工（如张三的加班、赵六的部门），需填 employeeName 与 employeeTopic
            - DEPT_OVERTIME / DEPT_HEADCOUNT / DEPT_TURNOVER / DEPT_PERFORMANCE / DEPT_SALARY：部门级统计（经理及以上）
            - COMPANY_OVERVIEW：公司/整体 HR 概览（经理及以上）
            - TEXT_TO_SQL：复杂分析（对比、排名、筛选组合、各部门对比、找出满足多条件的员工）

            employeeTopic 仅 NAMED_EMPLOYEE 时填写：LEAVE|OVERTIME|SALARY|PERFORMANCE|ATTENDANCE|TURNOVER|SATISFACTION|PROFILE

            路由规则：
            1. 制度/政策/流程/福利类 → KNOWLEDGE（即使含「列出」「哪些」）
            2. 「赵六的部门」「张三在哪个部门」→ NAMED_EMPLOYEE + PROFILE（不是部门聚合统计）
            3. 「对比各部门」「哪个部门加班最多」→ TEXT_TO_SQL 或 DEPT_*（不是 NAMED_EMPLOYEE）
            4. EMPLOYEE 角色查他人 → 仍标 NAMED_EMPLOYEE（权限由下游拦截）
            5. EMPLOYEE 查公司/部门整体统计 → KNOWLEDGE
            6. 含对比/排名/超过/低于/同时/且/平均/各部门/找出/筛选/排序 → 优先 TEXT_TO_SQL

            示例：
            Q: 年假有多少天 → {"intent":"KNOWLEDGE","employeeName":null,"employeeTopic":null}
            Q: 我的加班时长 → {"intent":"PERSONAL_OVERTIME","employeeName":null,"employeeTopic":null}
            Q: 赵六的加班时长 → {"intent":"NAMED_EMPLOYEE","employeeName":"赵六","employeeTopic":"OVERTIME"}
            Q: 赵六的部门是哪个 → {"intent":"NAMED_EMPLOYEE","employeeName":"赵六","employeeTopic":"PROFILE"}
            Q: 赵六和赵六一画像 → {"intent":"NAMED_EMPLOYEE","employeeName":"赵六","employeeNames":["赵六","赵六一"],"employeeTopic":"PROFILE"}
            Q: 对比各部门加班时长 → {"intent":"TEXT_TO_SQL","employeeName":null,"employeeTopic":null}
            Q: 统计本季度部门加班时长 → {"intent":"DEPT_OVERTIME","employeeName":null,"employeeTopic":null}
            Q: 列出公司福利类型 → {"intent":"KNOWLEDGE","employeeName":null,"employeeTopic":null}
            """;

    public String buildSystemPrompt(UserPrincipal user) {
        return SYSTEM_PROMPT + "\n当前用户角色: " + user.getRole()
                + "\n当前用户员工编号: " + user.getEmployeeId()
                + "\n当前用户部门: " + user.getDepartmentId()
                + roleHint(user.getRole());
    }

    public String buildUserPrompt(String question) {
        return "用户问题：\n" + question;
    }

    private String roleHint(UserRole role) {
        return switch (role) {
            case EMPLOYEE -> "\n角色约束：只能查本人数据；查他人标 NAMED_EMPLOYEE；查公司/部门整体统计标 KNOWLEDGE。";
            case MANAGER -> "\n角色约束：可查本部门员工与部门统计；复杂分析标 TEXT_TO_SQL。";
            case HRBP, HR_ADMIN -> "\n角色约束：可查全公司数据；复杂分析标 TEXT_TO_SQL。";
        };
    }
}
