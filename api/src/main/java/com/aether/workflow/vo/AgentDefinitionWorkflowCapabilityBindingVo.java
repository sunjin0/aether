package com.aether.workflow.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentDefinitionWorkflowCapabilityBindingVo extends BaseEntity {
    private String capabilityId;
    private Integer priority;
    private Integer status;
    private String capabilityCode;
    private String displayName;
    private String description;
    private String workflowId;
    private String workflowVersionId;
    private String riskLevel;
}
