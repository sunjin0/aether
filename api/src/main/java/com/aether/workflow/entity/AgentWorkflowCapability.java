package com.aether.workflow.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 可调用的工作流能力。能力绑定一个不可变的已发布工作流版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_capability")
public class AgentWorkflowCapability extends AccountOwnedEntity {
    private String applicationId;
    private String workflowId;
    private String workflowVersionId;
    private String capabilityCode;
    private String displayName;
    private String description;
    private Boolean enabled;
    private Integer status;
    private String allowedActions;
    private String agentWritableVariables;
    private String allowedEventTypes;
    private String riskLevel;
    private String policyJson;
    private String inputSchema;
    private String outputSchema;
}
