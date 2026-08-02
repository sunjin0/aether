package com.aether.workflow.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowSensitiveDataSanitizerTest {
    @Test
    void masksConfiguredFieldsRecursivelyWithoutChangingOtherValues() {
        WorkflowSensitiveDataSanitizer sanitizer = new WorkflowSensitiveDataSanitizer("secret,token");
        String result = sanitizer.sanitizeJson("{\"name\":\"ok\",\"secret\":\"top\",\"nested\":{\"TOKEN\":\"abc\"}}");
        assertEquals("{\"name\":\"ok\",\"secret\":\"***\",\"nested\":{\"TOKEN\":\"***\"}}", result);
    }
}
