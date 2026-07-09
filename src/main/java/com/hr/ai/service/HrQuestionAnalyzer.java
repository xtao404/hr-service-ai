package com.hr.ai.service;

import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.springframework.stereotype.Component;

@Component
public class HrQuestionAnalyzer {

    private static final String CURRENT_QUARTER = "2026-Q1";

    private final TextToSqlProperties textToSqlProperties;

    public HrQuestionAnalyzer(TextToSqlProperties textToSqlProperties) {
        this.textToSqlProperties = textToSqlProperties;
    }

    public HrQueryIntent analyze(String question, UserPrincipal user) {
        String q = question.toLowerCase();

        if (textToSqlProperties.isEnabled() && shouldUseTextToSql(q, user)) {
            return HrQueryIntent.TEXT_TO_SQL;
        }

        if (isPersonal(q)) {
            if (containsAny(q, "假期", "年假", "余额", "请假")) return HrQueryIntent.PERSONAL_LEAVE;
            if (containsAny(q, "加班")) return HrQueryIntent.PERSONAL_OVERTIME;
            if (containsAny(q, "薪资", "工资", "薪酬")) return HrQueryIntent.PERSONAL_SALARY;
            if (containsAny(q, "绩效", "考核", "评级")) return HrQueryIntent.PERSONAL_PERFORMANCE;
            if (containsAny(q, "考勤", "迟到", "缺勤")) return HrQueryIntent.PERSONAL_ATTENDANCE;
            return HrQueryIntent.PERSONAL_PROFILE;
        }

        if (isManagerOrAbove(user.getRole())) {
            if (containsAny(q, "离职", "风险", "流失", "挽留")) return HrQueryIntent.DEPT_TURNOVER;
            if (containsAny(q, "加班", "工时")) return HrQueryIntent.DEPT_OVERTIME;
            if (containsAny(q, "人数", "在职", "headcount", "编制")) return HrQueryIntent.DEPT_HEADCOUNT;
            if (containsAny(q, "绩效", "考核", "表现")) return HrQueryIntent.DEPT_PERFORMANCE;
            if (containsAny(q, "薪酬", "工资", "薪资", "竞争力")) return HrQueryIntent.DEPT_SALARY;
            if (containsAny(q, "统计", "分析", "报告", "汇总", "概览", "整体", "公司")) {
                return HrQueryIntent.COMPANY_OVERVIEW;
            }
        }

        if (containsAny(q, "统计", "分析", "多少", "哪些", "排名", "对比", "汇总")) {
            if (user.getRole() == UserRole.EMPLOYEE) {
                return HrQueryIntent.KNOWLEDGE;
            }
            return HrQueryIntent.COMPANY_OVERVIEW;
        }

        return HrQueryIntent.KNOWLEDGE;
    }

    public String currentQuarter() {
        return CURRENT_QUARTER;
    }

    private boolean shouldUseTextToSql(String q, UserPrincipal user) {
        if (user.getRole() == UserRole.EMPLOYEE && !isPersonal(q)) {
            return false;
        }
        return containsAny(q,
                "对比", "比较", "排名", "最高", "最低", "超过", "低于", "高于", "大于", "小于",
                "同时", "并且", "且", "以及", "平均", "各部门", "哪个部门", "哪一", "分组",
                "列出", "找出", "筛选", "组合", "交叉", "top", "前", "后",
                "满意度", "绩效.*加班", "加班.*绩效",
                "分布", "占比", "比例", "趋势", "最多", "最少", "大于等于", "小于等于",
                "按部门", "按岗位", "按评级", "按等级", "排序", "从高到低", "从低到高");
    }

    private boolean isPersonal(String q) {
        return q.contains("我的") || q.contains("我") && !containsAny(q, "我们", "部门", "公司", "团队", "哪个");
    }

    private boolean isManagerOrAbove(UserRole role) {
        return role == UserRole.MANAGER || role == UserRole.HRBP || role == UserRole.HR_ADMIN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (keyword.contains(".*")) {
                if (text.matches(".*" + keyword + ".*")) return true;
            } else if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
