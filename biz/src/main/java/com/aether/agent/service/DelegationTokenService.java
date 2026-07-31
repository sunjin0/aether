package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class DelegationTokenService {

    private static final long TOKEN_TTL_MINUTES = 5;
    private final DeepAgentConfig config;

    public DelegationTokenService(DeepAgentConfig config) {
        this.config = config;
    }

    public String create(String runId, String userId, String agentId, List<String> allowedTools) {
        long now = System.currentTimeMillis();
        return JWT.create()
                .withClaim("runId", runId)
                .withClaim("userId", userId)
                .withClaim("agentId", agentId)
                .withClaim("allowedTools", allowedTools)
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + TOKEN_TTL_MINUTES * 60 * 1000))
                .sign(Algorithm.HMAC256(config.getMcpDelegationSecret()));
    }
}
