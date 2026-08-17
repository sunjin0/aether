package com.aether.agent.sandbox.dto;

import lombok.Data;

/**
 * 表示SandboxAudit查询DTO。
 */
@Data
public class SandboxAuditQueryDto {
    private Long current = 1L;
    private Long pageSize = 20L;
    private String status, templateCode, requesterUserId, agentDefinitionId, approverUserId, riskLevel;
    private Long startTime, endTime;
}
