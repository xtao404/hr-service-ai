package com.hr.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerNumberValidatorTest {

    private AnswerNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AnswerNumberValidator();
    }

    @Test
    void groundedNumbers_pass() {
        String source = "赵六 | 68.5 | 技术研发部";
        String answer = "赵六本季度加班 68.5 小时，所属技术研发部。";
        assertFalse(validator.hasUngroundedNumbers(answer, source));
    }

    @Test
    void inventedNumbers_fail() {
        String source = "赵六 | 68.5 | 技术研发部";
        String answer = "赵六本季度加班 120 小时。";
        assertTrue(validator.hasUngroundedNumbers(answer, source));
    }

    @Test
    void smallOrdinals_ignored() {
        String source = "共 3 行结果";
        String answer = "以下是前 3 条记录。";
        assertFalse(validator.hasUngroundedNumbers(answer, source));
    }
}
