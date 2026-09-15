package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 对工作流调用发出的受控命令及其幂等结果。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_invocation_command")
public class AgentWorkflowInvocationCommand extends BaseEntity {
    private String tenantId;
    private String applicationId;
    private String invocationId;
    private String commandType;
    private String operationKey;
    private String commandPayload;
    private String requestedByType;
    private String requestedById;
    private Long expectedStateVersion;
    private String status;
    private String resultCode;
    private String resultSummary;
    private Long requestedAt;
    private Long appliedAt;
}
