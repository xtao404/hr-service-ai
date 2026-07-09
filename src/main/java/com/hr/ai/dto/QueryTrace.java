package com.hr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueryTrace {
    /** KNOWLEDGE | PRESET_QUERY | TEXT_TO_SQL */
    private String routeType;
    private String intent;
    private String intentLabel;
    private String dataSource;
    private String queryMethod;
    private String generatedSql;
    private String permissionNote;
    private Integer rowCount;
    private List<EmployeeRef> employees;
}
