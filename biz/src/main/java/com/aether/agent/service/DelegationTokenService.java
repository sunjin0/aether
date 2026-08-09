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

@Component
public class DelegationTokenService {

    private static final long TOKEN_TTL_MINUTES = 5;
    private final DeepAgentConfig config;

    public DelegationTokenService(DeepAgentConfig config) {
        this.config = config;
    }

    public String create(String runId, String userId, String agentId, List<String> allowedTools) {
        return create(runId, userId, agentId, allowedTools, Collections.<String>emptyList());
    }

    /** Artifact-capable Skills are carried as a separate claim so the sandbox can enforce the frozen capability set. */
    public String create(String runId, String userId, String agentId, List<String> allowedTools,
                         List<String> artifactSkillCodes) {
        if (StringUtils.isBlank(config.getMcpDelegationSecret())) {
            throw new ServerException(500, I18nUtils.getMessage("agent.mcp.delegation-secret.missing"));
        }
        long now = System.currentTimeMillis();
        return JWT.create()
                .withClaim("runId", runId)
                .withClaim("userId", userId)
                .withClaim("agentId", agentId)
                .withClaim("allowedTools", allowedTools)
                .withClaim("artifactSkillCodes", artifactSkillCodes == null ? Collections.<String>emptyList() : artifactSkillCodes)
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + TOKEN_TTL_MINUTES * 60 * 1000))
                .sign(Algorithm.HMAC256(config.getMcpDelegationSecret()));
    }

    /** Creates a least-privilege token for the internal document conversion endpoint. */
    public String createDocumentProcessingToken() {
        return create("document-processing", "system", "chat-attachment",
                Collections.singletonList("process_document"));
    }
}
