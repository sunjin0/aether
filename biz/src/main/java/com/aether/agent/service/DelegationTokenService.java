package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Collections;
import java.util.List;

/**
 * 定义Delegation令牌业务服务契约。
 */
@Component
public class DelegationTokenService {

    private final DeepAgentConfig config;

    /**
     * 创建 {@code DelegationTokenService} 实例。
     */
    public DelegationTokenService(DeepAgentConfig config) {
        this.config = config;
    }

    /**
     * 创建当前请求。
     */
    public String create(String runId, String userId, String agentId, List<String> allowedTools) {
        return create(runId, userId, agentId, allowedTools, null, null, null);
    }

    /** Adds server-resolved product scope to the short-lived delegation token. */
    public String create(String runId, String userId, String agentId, List<String> allowedTools,
                         String applicationId, String productProfileId, String serviceAccountId) {
        if (StringUtils.isBlank(config.getMcpDelegationSecret())) {
            throw new ServerException(500, I18nUtils.getMessage("agent.mcp.delegation-secret.missing"));
        }
        long now = System.currentTimeMillis();
        long ttlMillis = Math.max(config.getDelegationTokenTtlSeconds(), 60L) * 1000;
        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withClaim("runId", runId)
                .withClaim("userId", userId)
                .withClaim("agentId", agentId)
                .withClaim("allowedTools", allowedTools)
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + ttlMillis));
        if (StringUtils.isNotBlank(applicationId)) builder.withClaim("applicationId", applicationId);
        if (StringUtils.isNotBlank(productProfileId)) builder.withClaim("productProfileId", productProfileId);
        if (StringUtils.isNotBlank(serviceAccountId)) builder.withClaim("serviceAccountId", serviceAccountId);
        return builder.sign(Algorithm.HMAC256(config.getMcpDelegationSecret()));
    }

    /**
     * Creates a least-privilege token for the internal document conversion endpoint.
     */
    public String createDocumentProcessingToken() {
        return create("document-processing", "system", "chat-attachment",
                Collections.singletonList("process_document"));
    }
}
