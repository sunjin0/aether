package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One-time, frozen dispatch ticket sent from the platform to the sandbox runner.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_sandbox_execution")
public class AgentSandboxExecution extends BaseEntity {
    private String runId;
    private String skillVersionId;
    private String messageId;
    private String userId;
    private String agentDefinitionId;
    private String executionConfigSnapshot;
    private String resourceSnapshot;
    private String inputJson;
    private String tokenHash;
    private Integer status;
    private Long expiresAt;
    private Long startedAt;
    private Long completedAt;
    private String logSummary;
    private String failureReason;
}
