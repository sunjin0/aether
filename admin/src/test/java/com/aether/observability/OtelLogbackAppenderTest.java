package com.aether.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelLogbackAppenderTest {
    @Test
    void redactRemovesSensitiveValuesFromExportedBody() {
        String result = OtelLogbackAppender.redact("token=abc123 authorization: Bearer-secret password = p@ss");
        assertFalse(result.contains("abc123"));
        assertFalse(result.contains("Bearer-secret"));
        assertFalse(result.contains("p@ss"));
        assertTrue(result.contains("[REDACTED]"));
    }
}
