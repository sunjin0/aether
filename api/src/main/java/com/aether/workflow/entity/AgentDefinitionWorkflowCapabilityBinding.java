package com.aether.workflow.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 绑定的工作流能力；能力定义本身不持有 Agent 绑定关系。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_definition_workflow_capability_binding")
public class AgentDefinitionWorkflowCapabilityBinding extends AccountOwnedEntity {
    private String agentDefinitionId;
    private String capabilityId;
    private Integer priority;
    private Integer status;
}
