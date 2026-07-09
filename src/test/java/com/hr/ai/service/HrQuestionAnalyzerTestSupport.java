package com.hr.ai.service;

import com.hr.ai.config.IntentRouterProperties;
import com.hr.ai.config.LlmProperties;
import com.hr.ai.config.PresetQueryProperties;
import com.hr.ai.config.TextToSqlProperties;
import com.hr.ai.model.entity.User;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.intent.EmployeeNameExtractor;
import com.hr.ai.service.intent.RuleBasedIntentAnalyzer;

/**
 * 单元测试用：强制 rule 模式，不依赖 LLM。
 */
final class HrQuestionAnalyzerTestSupport {

    private HrQuestionAnalyzerTestSupport() {
    }

    static HrQuestionAnalyzer createRuleBasedAnalyzer(TextToSqlProperties textToSqlProperties,
                                                      PresetQueryProperties presetQueryProperties) {
        IntentRouterProperties intentRouterProperties = new IntentRouterProperties();
        intentRouterProperties.setMode("rule");
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setProvider("mock");
        return new HrQuestionAnalyzer(
                null,
                new RuleBasedIntentAnalyzer(new EmployeeNameExtractor()),
                intentRouterProperties,
                llmProperties,
                textToSqlProperties,
                presetQueryProperties
        );
    }

    static UserPrincipal user(UserRole role, String employeeId, String deptId) {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("pwd");
        user.setRole(role);
        user.setEmployeeId(employeeId);
        user.setDepartmentId(deptId);
        return new UserPrincipal(user);
    }
}
