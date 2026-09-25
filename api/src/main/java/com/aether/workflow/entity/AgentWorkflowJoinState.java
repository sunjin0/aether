package com.aether.workflow.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 并行分支的汇聚状态；每个实例、汇聚节点和 token 仅一条。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_join_state")
public class AgentWorkflowJoinState extends AccountOwnedEntity {
    private String instanceId, joinNodeId, tokenKey, joinMode, status, errorMessage;
    private Integer expectedCount, completedCount, failedCount;
}
