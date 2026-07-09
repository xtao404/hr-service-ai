package com.hr.ai.service.texttosql;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 LLM 原始输出中提取可执行的 SELECT 语句。
 * 支持 Markdown 代码块、前后缀说明文字、多条语句取首条 SELECT 等格式。
 */
@Component
public class SqlExtractor {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "(SELECT\\s.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public String extract(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("未生成有效 SQL");
        }

        String text = raw.trim();

        // 1. Markdown ```sql ... ``` 或 ``` ... ```
        if (text.contains("```")) {
            text = extractFromMarkdown(text);
        }

        // 2. 截取从 SELECT 开始的部分（忽略 LLM 前置说明）
        Matcher matcher = SELECT_PATTERN.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).trim();
        }

        // 3. 去除末尾分号及 trailing 说明
        text = stripTrailingSemicolon(text);

        // 4. 规范化空白
        text = text.replaceAll("\\s+", " ").trim();

        if (text.isBlank()) {
            throw new IllegalArgumentException("未生成有效 SQL");
        }
        return text;
    }

    private String extractFromMarkdown(String text) {
        int start = text.indexOf("```");
        int end = text.lastIndexOf("```");
        if (start >= 0 && end > start) {
            String block = text.substring(start + 3, end).trim();
            if (block.toLowerCase(Locale.ROOT).startsWith("sql")) {
                block = block.substring(3).trim();
            }
            return block;
        }
        return text;
    }

    private String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
