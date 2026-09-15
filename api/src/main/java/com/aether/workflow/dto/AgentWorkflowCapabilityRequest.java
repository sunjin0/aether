package com.aether.workflow.dto;

import lombok.Data;

/** 工作流能力创建或更新请求。 */
@Data
public class AgentWorkflowCapabilityRequest {
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
