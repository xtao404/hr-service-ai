package com.hr.ai.dto;

import com.hr.ai.model.enums.EmployeeQueryTopic;
import com.hr.ai.model.enums.HrQueryIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassification {

    private HrQueryIntent intent;
    private String employeeName;
    private EmployeeQueryTopic employeeTopic;
    /** llm | rule */
    private String source;

    public Optional<NamedEmployeeQuery> toNamedEmployeeQuery() {
        if (intent != HrQueryIntent.NAMED_EMPLOYEE || employeeName == null || employeeName.isBlank()) {
            return Optional.empty();
        }
        EmployeeQueryTopic topic = employeeTopic != null ? employeeTopic : EmployeeQueryTopic.PROFILE;
        return Optional.of(new NamedEmployeeQuery(employeeName, topic));
    }
}
