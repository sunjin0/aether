package com.aether.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 审计载荷加密与解密的单元测试。 */
class AuditDataProtectionServiceTest {

    @Test
    void encryptsAndDecryptsWhenKeyIsConfigured() {
        AuditDataProtectionService service = new AuditDataProtectionService();
        ReflectionTestUtils.setField(service, "configuredKey", "test-audit-key");

        String encrypted = service.protect("包含敏感字段的审计内容");

        assertTrue(encrypted.startsWith("ENCv1:"));
        assertEquals("包含敏感字段的审计内容", service.unprotect(encrypted));
    }

    @Test
    void keepsLegacyPlaintextReadable() {
        AuditDataProtectionService service = new AuditDataProtectionService();
        assertEquals("历史明文", service.unprotect("历史明文"));
    }
}
