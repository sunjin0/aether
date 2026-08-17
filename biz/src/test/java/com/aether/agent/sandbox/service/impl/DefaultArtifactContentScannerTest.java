package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.service.ArtifactContentScanner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证DefaultArtifactContentScanner的行为。
 */
class DefaultArtifactContentScannerTest {
    private final DefaultArtifactContentScanner scanner = new DefaultArtifactContentScanner(true);

    /**
     * 处理blocksHighRiskSecretsAndIdentityDataWithoutReturningPlaintext。
     */
    @Test
    void blocksHighRiskSecretsAndIdentityDataWithoutReturningPlaintext() {
        ArtifactContentScanner.ScanResult secret = scanner.scan("result.json", "application/json", "{\"apiKey\":\"long-secret-value\"}".getBytes(StandardCharsets.UTF_8));
        ArtifactContentScanner.ScanResult identity = scanner.scan("result.csv", "text/csv", "11010519491231002X".getBytes(StandardCharsets.UTF_8));
        assertFalse(secret.isAllowed());
        assertEquals("HIGH_SECRET", secret.getRuleId());
        assertFalse(identity.isAllowed());
        assertEquals("HIGH_CN_ID", identity.getRuleId());
    }

    /**
     * 处理flagsContactDataButAllowsAConfiguredLocal任务ToContinue。
     */
    @Test
    void flagsContactDataButAllowsAConfiguredLocalTaskToContinue() {
        ArtifactContentScanner.ScanResult result = scanner.scan("result.csv", "text/csv", "person@example.com,13800138000".getBytes(StandardCharsets.UTF_8));
        assertTrue(result.isAllowed());
        assertEquals("PII_CONTACT", result.getRuleId());
    }
}
