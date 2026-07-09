package com.hr.ai.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验大模型答案中的数字是否均能在源数据中找到，防止臆造指标。
 */
@Component
public class AnswerNumberValidator {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Set<String> IGNORE = Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

    /**
     * @return true 表示答案含有源数据中不存在的可疑数字
     */
    public boolean hasUngroundedNumbers(String answer, String sourceData) {
        if (answer == null || answer.isBlank() || sourceData == null || sourceData.isBlank()) {
            return false;
        }
        Set<String> sourceNumbers = extractNumbers(sourceData);
        for (String num : extractNumbers(answer)) {
            if (shouldIgnore(num)) {
                continue;
            }
            if (!isGroundedInSource(num, sourceNumbers, sourceData)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGroundedInSource(String num, Set<String> sourceNumbers, String sourceData) {
        if (sourceNumbers.contains(num)) {
            return true;
        }
        if (sourceData.contains(num)) {
            return true;
        }
        try {
            double value = Double.parseDouble(num);
            for (String src : sourceNumbers) {
                try {
                    double srcVal = Double.parseDouble(src);
                    if (Math.abs(value - srcVal) < 0.01) {
                        return true;
                    }
                    if (Math.abs(value - srcVal * 100) < 0.5 || Math.abs(value * 100 - srcVal) < 0.5) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        } catch (NumberFormatException ignored) {
            // skip
        }
        return false;
    }

    private Set<String> extractNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.add(normalizeNumber(matcher.group()));
        }
        return numbers;
    }

    private String normalizeNumber(String num) {
        if (num == null) {
            return "";
        }
        if (num.contains(".")) {
            return num.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return num;
    }

    private boolean shouldIgnore(String num) {
        if (IGNORE.contains(num)) {
            return true;
        }
        try {
            double v = Double.parseDouble(num);
            return v >= 1900 && v <= 2100;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
