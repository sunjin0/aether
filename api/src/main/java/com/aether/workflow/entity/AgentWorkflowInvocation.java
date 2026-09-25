package com.aether.workflow.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 调用工作流的持久化关联。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_invocation")
public class AgentWorkflowInvocation extends AccountOwnedEntity {
    private String applicationId;
    private String capabilityId;
    private String workflowId;
    private String workflowVersionId;
    private String workflowInstanceId;
    private String agentDefinitionId;
    private String agentRunId;
    private String agentTaskId;
    private String principalType;
    private String principalId;
    private String serviceAccountId;
    private String toolCallId;
    private String operationKey;
    private String status;
    private String inputSnapshot;
    private String outputSnapshot;
    private Long startedAt;
    private Long completedAt;
}
