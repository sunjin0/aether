package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示SandboxExecution任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_task")
public class SandboxExecutionTask extends BaseEntity {
    private String tenantId;
    private String legacyExecutionId, templateId, templateVersionId, templateCode, requesterUserId, agentDefinitionId, runId, messageId;
    private String status, riskLevel, inputSnapshot, inputSha256, scriptSha256, configSnapshot, policyVersion, executionTokenHash;
    private String claimedBy, failureCode, failureReason, logSummary;
    private Boolean approvalRequired;
    private Long claimedAt, leaseExpiresAt, cancelRequestedAt, startedAt, completedAt, expiresAt, inputPurgedAt;
}
