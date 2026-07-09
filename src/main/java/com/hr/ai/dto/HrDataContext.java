package com.hr.ai.dto;

import com.hr.ai.model.enums.HrQueryIntent;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HrDataContext {
    private HrQueryIntent intent;
    private String dataSource;
    private String dataText;
    /** preset | text-to-sql */
    private String queryMethod;
    /** Text-to-SQL 生成的 SQL（仅展示，已做安全校验） */
    private String generatedSql;
    /** 查询结果行数（Text-to-SQL） */
    private Integer rowCount;
    /** 结构化查询结果，用于生成图表 */
    private List<Map<String, Object>> queryRows;
    /** 图表标题提示 */
    private String chartTitle;
}
