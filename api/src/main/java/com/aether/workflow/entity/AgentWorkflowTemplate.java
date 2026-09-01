package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可复用的工作流画布模板。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_template")
public class AgentWorkflowTemplate extends BaseEntity {
    private String tenantId;
    private String name;
    private String description;
    private String agentDefinitionId;
    private String nodes;
    private String edges;
    private String inputSchema;
    private String outputSchema;
    private String sourceWorkflowId;
    private Integer sourceVersion;
}
