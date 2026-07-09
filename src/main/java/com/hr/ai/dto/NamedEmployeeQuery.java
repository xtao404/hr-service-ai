package com.hr.ai.dto;

import com.hr.ai.model.enums.EmployeeQueryTopic;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NamedEmployeeQuery {
    private String employeeName;
    private List<String> employeeNames;
    private EmployeeQueryTopic topic;

    public NamedEmployeeQuery(String employeeName, EmployeeQueryTopic topic) {
        this.employeeName = employeeName;
        this.topic = topic;
        this.employeeNames = employeeName != null ? List.of(employeeName) : List.of();
    }

    public NamedEmployeeQuery(String employeeName, List<String> employeeNames, EmployeeQueryTopic topic) {
        this.employeeName = employeeName;
        this.employeeNames = employeeNames != null ? List.copyOf(employeeNames) : List.of();
        this.topic = topic;
    }

    public List<String> resolvedNames() {
        if (employeeNames != null && !employeeNames.isEmpty()) {
            return employeeNames;
        }
        if (employeeName != null && !employeeName.isBlank()) {
            return List.of(employeeName);
        }
        return List.of();
    }

    public boolean isMultiEmployee() {
        return resolvedNames().size() > 1;
    }
}
