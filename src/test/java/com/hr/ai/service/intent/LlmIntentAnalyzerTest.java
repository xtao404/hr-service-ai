package com.hr.ai.service.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.ai.config.IntentRouterProperties;
import com.hr.ai.config.LlmProperties;
import com.hr.ai.dto.IntentClassification;
import com.hr.ai.model.enums.EmployeeQueryTopic;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.HrQuestionAnalyzerTestSupport;
import com.hr.ai.service.QwenChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmIntentAnalyzerTest {

    private LlmIntentAnalyzer llmIntentAnalyzer;

    @BeforeEach
    void setUp() {
        QwenChatClient qwenChatClient = mock(QwenChatClient.class);
        when(qwenChatClient.chat(anyString(), anyString(), anyDouble())).thenReturn("""
                {"intent":"NAMED_EMPLOYEE","employeeName":"赵六","employeeTopic":"PROFILE"}
                """);

        IntentRouterProperties routerProperties = new IntentRouterProperties();
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setApiKey("test-key");

        llmIntentAnalyzer = new LlmIntentAnalyzer(
                qwenChatClient,
                llmProperties,
                routerProperties,
                new IntentRouterPromptBuilder(),
                new ObjectMapper()
        );
    }

    @Test
    void classify_parsesNamedEmployeeDepartment() {
        UserPrincipal manager = HrQuestionAnalyzerTestSupport.user(UserRole.MANAGER, "M001", "D001");
        IntentClassification result = llmIntentAnalyzer.classify("赵六的部门是哪个", manager);
        assertEquals(HrQueryIntent.NAMED_EMPLOYEE, result.getIntent());
        assertEquals("赵六", result.getEmployeeName());
        assertEquals(EmployeeQueryTopic.PROFILE, result.getEmployeeTopic());
        assertEquals("llm", result.getSource());
    }
}
