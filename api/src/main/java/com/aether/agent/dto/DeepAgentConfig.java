package com.aether.agent.dto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 表示Deep智能体配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aether.deep-agent")
public class DeepAgentConfig {
    private String baseUrl;
    private String sharedSecret;
    private String keyId = "deep-agent-v1";
    private String mcpDelegationSecret;
    /** AES-GCM 密钥材料；必须与 MCP 的 AETHER_MCP_CREDENTIAL_SECRET 一致。 */
    private String mcpCredentialSecret;
    private long runTimeoutSeconds = 600L;
    /**
     * MCP 委派令牌有效期(秒)。默认 30 分钟：必须大于 deep-agent 单次运行超时
     * (runTimeoutSeconds)，避免长任务/带审批等待的运行在工具调用中途因令牌过期被拒。
     */
    private long delegationTokenTtlSeconds = 1800L;
    /**
     * Deep 运行卡死(长时间无终态回调)的超时阈值(秒)；超过后由扫描器标记失败。默认 30 分钟。
     */
    private long staleRunTimeoutSeconds = 1800L;
    /**
     * 卡死运行扫描间隔(毫秒)。
     */
    private long timeoutScanIntervalMs = 30000L;
}
