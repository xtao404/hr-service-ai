package com.hr.ai.service;

import com.hr.ai.config.AntiHallucinationProperties;
import com.hr.ai.config.LlmProperties;
import com.hr.ai.config.RagProperties;
import com.hr.ai.dto.HrDataContext;
import com.hr.ai.model.enums.HrQueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmServiceAntiHallucinationTest {

    private LlmService llmService;
    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setMinConfidenceForAnswer(0.15);
        ragProperties.setLowScoreFallbackEnabled(false);

        vectorStoreService = new VectorStoreService(null, ragProperties);

        AntiHallucinationProperties antiHallucinationProperties = new AntiHallucinationProperties();
        antiHallucinationProperties.setSkipLlmForStructuredData(true);
        antiHallucinationProperties.setStructuredDataMaxRows(20);

        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setProvider("mock");

        llmService = new LlmService(
                llmProperties,
                antiHallucinationProperties,
                null,
                vectorStoreService,
                new StructuredDataAnswerFormatter(),
                new AnswerNumberValidator()
        );
    }

    @Test
    void generateDataAnswer_usesTemplateForStructuredRows() {
        HrDataContext context = new HrDataContext();
        context.setIntent(HrQueryIntent.TEXT_TO_SQL);
        context.setDataSource("HR业务数据库");
        context.setQueryMethod("text-to-sql");
        context.setRowCount(2);
        context.setDataText("共 2 行结果:\n1. 赵六 | 68.5\n2. 钱七 | 55.0");
        context.setQueryRows(List.of(
                Map.of("姓名", "赵六", "加班", 68.5),
                Map.of("姓名", "钱七", "加班", 55.0)));

        String answer = llmService.generateDataAnswer("部门加班情况", context);
        assertTrue(answer.contains("68.5"));
        assertTrue(answer.contains("HR业务系统"));
        assertFalse(answer.contains("【大模型服务暂时不可用"));
    }

    @Test
    void generateAnswer_refusesWhenNoContext() {
        String answer = llmService.generateAnswer("赵六的部门是哪个", List.of());
        assertTrue(answer.contains("没有找到"));
    }
}
