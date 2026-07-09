package com.hr.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class QueryTrace {
    /** KNOWLEDGE | PRESET_QUERY | TEXT_TO_SQL */
    private String routeType;
    /** THINKING | ANSWERING | DONE */
    private String status;
    /** ANALYZE | RETRIEVE | QUERY | GENERATE | COMPLETE */
    private String stage;
    private String stageLabel;
    private String progressMessage;
    private String intent;
    private String intentLabel;
    private String dataSource;
    private String queryMethod;
    /** 仅服务端日志使用，不向前端/API 暴露（SQL-21/22） */
    @JsonIgnore
    private String generatedSql;
    private String permissionNote;
    private Integer rowCount;
    private List<EmployeeRef> employees;
}
