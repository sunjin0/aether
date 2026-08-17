package com.aether.agent.sandbox.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证SandboxLogRedaction的行为。
 */
class SandboxLogRedactionTest {
    /**
     * 处理removesSecretsAndCommonPiiFromAuditSummaries。
     */
    @Test
    void removesSecretsAndCommonPiiFromAuditSummaries() throws Exception {
        Method redact = SandboxTaskServiceImpl.class.getDeclaredMethod("redact", String.class);
        redact.setAccessible(true);
        String value = (String) redact.invoke(newServiceWithoutDependencies(), "{\"apiKey\":\"top-secret-value\",\"id\":\"11010519491231002X\",\"card\":\"6222021234567890123\",\"mail\":\"person@example.com\"}");
        assertFalse(value.contains("top-secret-value"));
        assertFalse(value.contains("11010519491231002X"));
        assertFalse(value.contains("6222021234567890123"));
        assertFalse(value.contains("person@example.com"));
    }

    /**
     * 处理new服务WithoutDependencies。
     */
    private SandboxTaskServiceImpl newServiceWithoutDependencies() {
        return new SandboxTaskServiceImpl(null, null, null, null, null, null, null, null, null, null, null, null, null, "bucket", "token");
    }
}
