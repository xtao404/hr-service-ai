package com.hr.ai.service.intent;

import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.enums.EmployeeQueryTopic;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从自然语言问题中提取一个或多个员工姓名（支持「赵六和赵六一画像」）。
 */
@Component
public class EmployeeNameExtractor {

    private static final String TOPIC_SUFFIX = "假期|年假|请假|加班|工时|工资|薪资|薪酬|绩效|考核|评级|表现"
            + "|考勤|迟到|缺勤|离职|风险|流失|满意度|档案|信息|资料|部门|画像";

    private static final Pattern TRAILING_TOPIC = Pattern.compile(
            "(的)?(" + TOPIC_SUFFIX + ")+$");

    private static final Set<String> NON_EMPLOYEE_NAME_WORDS = Set.of(
            "查询", "部门", "公司", "我们", "人力", "资源", "薪酬", "工资", "薪资", "员工", "同事",
            "我们部门", "哪个部门", "哪些员工", "所有员工", "全体员", "人力资源",
            "年假", "假期", "加班", "绩效", "考勤", "离职", "风险", "满意度", "档案", "画像", "信息", "资料");

    private static final Pattern NAMED_EMPLOYEE_WITH_DE = Pattern.compile(
            "(?:查询|查|问)([\\u4e00-\\u9fa5]{2,4})的|([\\u4e00-\\u9fa5]{2,4})的");

    private static final Pattern NAMED_EMPLOYEE_DIRECT = Pattern.compile(
            "(?:查询|查|问)?([\\u4e00-\\u9fa5]{2,4})(?:" + TOPIC_SUFFIX + ")");

    private static final Pattern NAMED_EMPLOYEE_SALARY = Pattern.compile(
            "(?:查询|查|问)?([\\u4e00-\\u9fa5]{2,4})(?:的)?(?:工资|薪资|薪酬)(?:是多少|多少)?");

    private static final Pattern NAMED_EMPLOYEE_DEPARTMENT = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,4})(?:在哪个|在哪一个|在什么|所属|所在)?(?:部门|部)");

    public Optional<NamedEmployeeQuery> extractQuery(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String q = question.toLowerCase(Locale.ROOT);
        List<String> names = extractNames(question);
        if (names.isEmpty()) {
            return Optional.empty();
        }
        EmployeeQueryTopic topic = inferTopic(q);
        if (names.size() == 1) {
            return Optional.of(new NamedEmployeeQuery(names.get(0), topic));
        }
        return Optional.of(new NamedEmployeeQuery(names.get(0), names, topic));
    }

    public List<String> extractNames(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (isAggregateEmployeeQuery(question)) {
            return List.of();
        }
        List<String> multi = extractMultiEmployeeNames(question);
        if (multi.size() >= 2) {
            return multi;
        }
        return extractSingleEmployeeName(question).map(List::of).orElse(List.of());
    }

    private List<String> extractMultiEmployeeNames(String question) {
        String core = stripTrailingTopic(question.trim());
        core = core.replaceAll("^(查询|查|问)", "").trim();
        if (!containsMultiDelimiter(core)) {
            return List.of();
        }
        String[] parts = core.split("[和与及、,，]+");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String part : parts) {
            String name = normalizeNameToken(part);
            if (isValidEmployeeName(name)) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private Optional<String> extractSingleEmployeeName(String question) {
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
        Matcher departmentMatcher = NAMED_EMPLOYEE_DEPARTMENT.matcher(question);
        if (departmentMatcher.find()) {
            String name = departmentMatcher.group(1);
            if (isValidEmployeeName(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private boolean isAggregateEmployeeQuery(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (extractMultiEmployeeNames(question).size() >= 2) {
            return false;
        }
        if (extractSingleEmployeeName(question).isPresent() && isNamedEmployeeDepartmentQuery(q)) {
            return false;
        }
        return containsAny(q, "部门", "各部门", "公司", "竞争力", "平均", "均值",
                "对比", "统计", "分析", "排名", "汇总", "概览", "整体", "哪个部门", "哪些");
    }

    private boolean isNamedEmployeeDepartmentQuery(String q) {
        if (containsAny(q, "的部门", "所属部门", "所在部门", "是什么部门", "什么部门")) {
            return !containsAny(q, "各部门", "对比", "统计", "排名", "平均", "汇总");
        }
        return containsAny(q, "哪个部门", "哪一部门", "哪个部")
                && !containsAny(q, "各部门", "对比", "统计", "排名", "平均", "汇总", "加班最多", "人数最多");
    }

    private String stripTrailingTopic(String text) {
        Matcher matcher = TRAILING_TOPIC.matcher(text);
        if (matcher.find()) {
            return text.substring(0, matcher.start()).trim();
        }
        return text;
    }

    private boolean containsMultiDelimiter(String text) {
        return text.contains("和") || text.contains("与") || text.contains("及")
                || text.contains("、") || text.contains(",") || text.contains("，");
    }

    private EmployeeQueryTopic inferTopic(String q) {
        if (containsAny(q, "假期", "年假", "余额", "请假")) return EmployeeQueryTopic.LEAVE;
        if (containsAny(q, "加班", "工时")) return EmployeeQueryTopic.OVERTIME;
        if (containsAny(q, "薪资", "工资", "薪酬")) return EmployeeQueryTopic.SALARY;
        if (containsAny(q, "绩效", "考核", "评级", "表现")) return EmployeeQueryTopic.PERFORMANCE;
        if (containsAny(q, "考勤", "迟到", "缺勤")) return EmployeeQueryTopic.ATTENDANCE;
        if (containsAny(q, "离职", "风险", "流失", "挽留")) return EmployeeQueryTopic.TURNOVER;
        if (containsAny(q, "满意度")) return EmployeeQueryTopic.SATISFACTION;
        if (containsAny(q, "部门", "所属", "所在")) return EmployeeQueryTopic.PROFILE;
        if (containsAny(q, "画像", "档案", "信息", "资料")) return EmployeeQueryTopic.PROFILE;
        return EmployeeQueryTopic.PROFILE;
    }

    private String normalizeNameToken(String part) {
        if (part == null || part.isBlank()) {
            return "";
        }
        String cleaned = stripTrailingTopic(part.trim());
        cleaned = cleaned.replaceAll("的$", "").trim();
        Matcher matcher = Pattern.compile("^[\\u4e00-\\u9fa5]{2,4}").matcher(cleaned);
        return matcher.find() ? matcher.group() : cleaned;
    }

    private boolean isValidEmployeeName(String name) {
        return name != null && name.matches("[\\u4e00-\\u9fa5]{2,4}") && !NON_EMPLOYEE_NAME_WORDS.contains(name);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
