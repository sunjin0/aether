package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DelegationTokenServiceTest {

    private static final String SECRET = "test-delegation-secret";

    @Test
    void tokenExpiryUsesConfiguredTtl() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpDelegationSecret(SECRET);
        config.setDelegationTokenTtlSeconds(1800L);

        DelegationTokenService service = new DelegationTokenService(config);
        String token = service.create("run-1", "user-1", "agent-1", Collections.singletonList("mcp-tool"));

        long ttlSeconds = (JWT.decode(token).getExpiresAt().getTime() - JWT.decode(token).getIssuedAt().getTime()) / 1000;
        assertEquals(1800L, ttlSeconds);
    }

    @Test
    void ttlFloorsAtSixtySecondsToKeepTokensShortLived() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpDelegationSecret(SECRET);
        config.setDelegationTokenTtlSeconds(10L);

        DelegationTokenService service = new DelegationTokenService(config);
        String token = service.create("run-1", "user-1", "agent-1", Collections.emptyList());

        long ttlSeconds = (JWT.decode(token).getExpiresAt().getTime() - JWT.decode(token).getIssuedAt().getTime()) / 1000;
        assertEquals(60L, ttlSeconds);
    }
}
