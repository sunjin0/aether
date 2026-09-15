package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流状态变化到 Agent 任务的持久化通知。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_invocation_outbox")
public class AgentWorkflowInvocationOutbox extends BaseEntity {
    private String tenantId;
    private String applicationId;
    private String invocationId;
    private String workflowInstanceId;
    private String eventType;
    private Long stateVersion;
    private String payload;
    private String status;
    private Integer attemptCount;
    private Long nextAttemptAt;
    private Long deliveredAt;
    private String lastError;
}
