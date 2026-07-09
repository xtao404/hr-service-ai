package com.hr.ai.service.intent;

import com.hr.ai.dto.NamedEmployeeQuery;
import com.hr.ai.model.enums.EmployeeQueryTopic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeNameExtractorTest {

    private final EmployeeNameExtractor extractor = new EmployeeNameExtractor();

    @Test
    void extractMultiEmployeeNames_fromAndDelimiter() {
        List<String> names = extractor.extractNames("赵六和赵六一画像");
        assertEquals(List.of("赵六", "赵六一"), names);
    }

    @Test
    void extractMultiEmployeeNames_fromCommaDelimiter() {
        List<String> names = extractor.extractNames("赵六、钱七的档案");
        assertEquals(List.of("赵六", "钱七"), names);
    }

    @Test
    void extractMultiEmployeeNames_withDeBeforeTopic() {
        List<String> names = extractor.extractNames("赵六和赵六一的画像");
        assertEquals(List.of("赵六", "赵六一"), names);
    }

    @Test
    void extractMultiEmployeeNames_withSpaceBeforeTopic() {
        List<String> names = extractor.extractNames("赵六和赵六一 画像");
        assertEquals(List.of("赵六", "赵六一"), names);
    }

    @Test
    void extractQuery_multiEmployeeProfile() {
        NamedEmployeeQuery query = extractor.extractQuery("赵六和赵六一画像").orElseThrow();
        assertTrue(query.isMultiEmployee());
        assertEquals(List.of("赵六", "赵六一"), query.resolvedNames());
        assertEquals(EmployeeQueryTopic.PROFILE, query.getTopic());
    }
}
