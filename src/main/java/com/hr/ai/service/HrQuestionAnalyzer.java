package com.hr.ai.service;

import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.enums.EmployeeQueryTopic;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HrQuestionAnalyzer {

    private static final String CURRENT_QUARTER = "2026-Q1";

    private static final String TOPIC_SUFFIX = "假期|年假|请假|加班|工时|工资|薪资|薪酬|绩效|考核|评级|表现"
            + "|考勤|迟到|缺勤|离职|风险|流失|满意度|档案|信息|资料";

    private static final Set<String> NON_EMPLOYEE_NAME_WORDS = Set.of(
            "查询", "部门", "公司", "我们", "人力", "资源", "薪酬", "工资", "薪资", "员工", "同事",
            "我们部门", "哪个部门", "哪些员工", "所有员工", "全体员", "人力资源",
            "年假", "假期", "加班", "绩效", "考勤", "离职", "风险", "满意度", "档案"
    );

    /** 问/查张三的假期、张三的假期 */
    private static final Pattern NAMED_EMPLOYEE_WITH_DE = Pattern.compile(
            "(?:查询|查|问)([\\u4e00-\\u9fa5]{2,4})的|([\\u4e00-\\u9fa5]{2,4})的");

    /** 问/查张三假期、张三假期（无「的」） */
    private static final Pattern NAMED_EMPLOYEE_DIRECT = Pattern.compile(
            "(?:查询|查|问)?([\\u4e00-\\u9fa5]{2,4})(?:" + TOPIC_SUFFIX + ")");

    /** 张三工资、查询张三薪酬 */
    private static final Pattern NAMED_EMPLOYEE_SALARY = Pattern.compile(
            "(?:查询|查|问)?([\\u4e00-\\u9fa5]{2,4})(?:的)?(?:工资|薪资|薪酬)(?:是多少|多少)?");

    private final TextToSqlProperties textToSqlProperties;

    public HrQuestionAnalyzer(TextToSqlProperties textToSqlProperties) {
        this.textToSqlProperties = textToSqlProperties;
    }

    public HrQueryIntent analyze(String question, UserPrincipal user) {
        String q = question.toLowerCase(Locale.ROOT);

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

        if (extractNamedEmployeeQuery(question).isPresent()) {
            return HrQueryIntent.NAMED_EMPLOYEE;
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

    public Optional<NamedEmployeeQuery> extractNamedEmployeeQuery(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String q = question.toLowerCase(Locale.ROOT);
        if (isAggregateEmployeeQuery(q)) {
            return Optional.empty();
        }
        return extractEmployeeName(question).map(name ->
                new NamedEmployeeQuery(name, inferEmployeeTopic(q)));
    }

    public Optional<String> extractEmployeeNameFromSalaryQuery(String question) {
        return extractNamedEmployeeQuery(question)
                .filter(nq -> nq.getTopic() == EmployeeQueryTopic.SALARY)
                .map(NamedEmployeeQuery::getEmployeeName);
    }

    public String currentQuarter() {
        return CURRENT_QUARTER;
    }

    private boolean isAggregateEmployeeQuery(String q) {
        return containsAny(q, "部门", "各部门", "公司", "竞争力", "平均", "均值",
                "对比", "统计", "分析", "排名", "汇总", "概览", "整体", "哪个部门", "哪些");
    }

    private Optional<String> extractEmployeeName(String question) {
        Matcher withDeMatcher = NAMED_EMPLOYEE_WITH_DE.matcher(question);
        if (withDeMatcher.find()) {
            String name = withDeMatcher.group(1) != null ? withDeMatcher.group(1) : withDeMatcher.group(2);
            if (isValidEmployeeName(name)) {
                return Optional.of(name);
            }
        }
        Matcher salaryMatcher = NAMED_EMPLOYEE_SALARY.matcher(question);
        if (salaryMatcher.find()) {
            String name = salaryMatcher.group(1);
            if (isValidEmployeeName(name)) {
                return Optional.of(name);
            }
        }
        Matcher directMatcher = NAMED_EMPLOYEE_DIRECT.matcher(question);
        if (directMatcher.find()) {
            String name = directMatcher.group(1);
            if (isValidEmployeeName(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private boolean isValidEmployeeName(String name) {
        return name != null && !name.isBlank() && !NON_EMPLOYEE_NAME_WORDS.contains(name);
    }

    private EmployeeQueryTopic inferEmployeeTopic(String q) {
        if (containsAny(q, "假期", "年假", "余额", "请假")) return EmployeeQueryTopic.LEAVE;
        if (containsAny(q, "加班", "工时")) return EmployeeQueryTopic.OVERTIME;
        if (containsAny(q, "薪资", "工资", "薪酬")) return EmployeeQueryTopic.SALARY;
        if (containsAny(q, "绩效", "考核", "评级", "表现")) return EmployeeQueryTopic.PERFORMANCE;
        if (containsAny(q, "考勤", "迟到", "缺勤")) return EmployeeQueryTopic.ATTENDANCE;
        if (containsAny(q, "离职", "风险", "流失", "挽留")) return EmployeeQueryTopic.TURNOVER;
        if (containsAny(q, "满意度")) return EmployeeQueryTopic.SATISFACTION;
        return EmployeeQueryTopic.PROFILE;
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
