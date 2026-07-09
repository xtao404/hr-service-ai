package com.hr.ai.dto;

import com.hr.ai.model.enums.EmployeeQueryTopic;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NamedEmployeeQuery {
    private String employeeName;
    private EmployeeQueryTopic topic;
}
