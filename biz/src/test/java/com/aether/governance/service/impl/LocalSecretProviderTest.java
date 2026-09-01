package com.aether.governance.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalSecretProviderTest {
    @Test
    void storesAndResolvesByScopedReference() {
        LocalSecretProvider provider = new LocalSecretProvider();
        provider.put("git-prod", "AGENT", "agent-1", Collections.singletonMap("token", "top-secret"));

        Map<String, String> resolved = provider.resolve("git-prod", "AGENT", "agent-1");
        assertEquals("top-secret", resolved.get("token"));
        assertTrue(provider.resolve("git-prod", "AGENT", "agent-2").isEmpty());
    }

    @Test
    void revokeRemovesSecret() {
        LocalSecretProvider provider = new LocalSecretProvider();
        provider.put("ref", "USER", "u1", Collections.singletonMap("key", "value"));
        provider.revoke("ref", "USER", "u1");
        assertTrue(provider.resolve("ref", "USER", "u1").isEmpty());
    }
}
