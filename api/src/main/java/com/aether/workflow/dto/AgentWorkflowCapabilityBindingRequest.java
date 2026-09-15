package com.aether.workflow.dto;

import lombok.Data;

@Data
public class AgentWorkflowCapabilityBindingRequest {
    private String capabilityId;
    private Integer priority;
    private Integer status;
}
