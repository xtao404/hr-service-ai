package com.hr.ai.service;

import com.hr.ai.dto.HrDataContext;
import org.springframework.stereotype.Component;

/**
 * 结构化查询结果模板直出，避免大模型改写数字或人员信息。
 */
@Component
public class StructuredDataAnswerFormatter {

    private static final String FOOTER = "\n\n以上数据来源于HR业务系统，如需进一步分析请联系HR。";

    public String format(String question, HrDataContext context) {
        String dataText = context.getDataText();
        if (dataText == null || dataText.isBlank()) {
            return "未能从数据库中查询到相关数据，请尝试换个方式描述您的问题，或联系HR确认。";
        }
        String source = context.getDataSource() != null ? context.getDataSource() : "HR业务数据库";
        StringBuilder sb = new StringBuilder();
        sb.append("根据").append(source).append("，针对您的问题「").append(question).append("」，查询结果如下：\n\n");
        sb.append(dataText.trim());
        if (context.getRowCount() != null && context.getRowCount() > 0) {
            sb.append("\n\n（共 ").append(context.getRowCount()).append(" 条记录）");
        }
        sb.append(FOOTER);
        return sb.toString();
    }

    public String formatRagRefusal(String question) {
        return "抱歉，我在知识库中没有找到与「" + question + "」足够相关的制度资料，无法给出准确答复。"
                + "建议您联系HR人工客服，或通过OA/HR系统查询相关业务数据。";
    }

    public String formatRagFromSnippet(String question, String docTitle, String snippet) {
        return String.format(
                "根据公司制度文件《%s》，针对您的问题「%s」，答复如下：\n\n%s%s",
                docTitle, question, snippet,
                "\n\n以上信息来源于公司内部知识库，如有疑问请联系HR部门确认。");
    }
}
