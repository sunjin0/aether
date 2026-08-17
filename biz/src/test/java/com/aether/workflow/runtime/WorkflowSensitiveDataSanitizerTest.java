package com.aether.workflow.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证工作流SensitiveDataSanitizer的行为。
 */
class WorkflowSensitiveDataSanitizerTest {
    /**
     * 处理masksConfiguredFieldsRecursivelyWithoutChangingOtherValues。
     */
    @Test
    void masksConfiguredFieldsRecursivelyWithoutChangingOtherValues() {
        WorkflowSensitiveDataSanitizer sanitizer = new WorkflowSensitiveDataSanitizer("secret,token");
        String result = sanitizer.sanitizeJson("{\"name\":\"ok\",\"secret\":\"top\",\"nested\":{\"TOKEN\":\"abc\"}}");
        assertEquals("{\"name\":\"ok\",\"secret\":\"***\",\"nested\":{\"TOKEN\":\"***\"}}", result);
    }
}
