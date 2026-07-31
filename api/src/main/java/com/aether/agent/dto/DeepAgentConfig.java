package com.aether.agent.dto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aether.deep-agent")
public class DeepAgentConfig {
    private String baseUrl;
    private String sharedSecret;
    private String keyId = "deep-agent-v1";
    private String mcpDelegationSecret;
    private long runTimeoutSeconds = 600L;
}
