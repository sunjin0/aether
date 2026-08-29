package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 节点完成时的脱敏变量快照，用于审计和调试，不作为运行时状态源。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_variable_snapshot")
public class AgentWorkflowVariableSnapshot extends BaseEntity {
    private String instanceId, nodeInstanceId, nodeId, snapshotStage, variables;
}
