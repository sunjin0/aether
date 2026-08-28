package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证Delegation令牌服务的行为。
 */
class DelegationTokenServiceTest {

    private static final String SECRET = "test-delegation-secret";

    /**
     * 令牌ExpiryUsesConfiguredTtl。
     */
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

    /**
     * 处理ttlFloorsAtSixtySecondsToKeepTokensShortLived。
     */
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

    @Test
    void productBoundaryIsSignedIntoDelegationToken() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpDelegationSecret(SECRET);
        DelegationTokenService service = new DelegationTokenService(config);

        String token = service.create("run-1", "user-1", "agent-1", Collections.singletonList("order_lookup"),
                "app-1", "product-v2", "sa-1");

        assertEquals("app-1", JWT.decode(token).getClaim("applicationId").asString());
        assertEquals("product-v2", JWT.decode(token).getClaim("productProfileId").asString());
        assertEquals("sa-1", JWT.decode(token).getClaim("serviceAccountId").asString());
    }
}
