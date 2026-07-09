package com.hr.ai.dto;

import com.hr.ai.model.enums.EmployeeQueryTopic;
import com.hr.ai.model.enums.HrQueryIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassification {

    private HrQueryIntent intent;
    private String employeeName;
    private List<String> employeeNames;
    private EmployeeQueryTopic employeeTopic;
    /** llm | rule */
    private String source;

    public Optional<NamedEmployeeQuery> toNamedEmployeeQuery() {
        if (intent != HrQueryIntent.NAMED_EMPLOYEE) {
            return Optional.empty();
        }
        EmployeeQueryTopic topic = employeeTopic != null ? employeeTopic : EmployeeQueryTopic.PROFILE;
        if (employeeNames != null && employeeNames.size() > 1) {
            return Optional.of(new NamedEmployeeQuery(employeeNames.get(0), employeeNames, topic));
        }
        if (employeeName == null || employeeName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new NamedEmployeeQuery(employeeName, topic));
    }
}
