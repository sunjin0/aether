package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 并行分支或子流程等待期间的节点执行令牌。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_node_token")
public class AgentWorkflowNodeToken extends BaseEntity {
    private String instanceId, nodeId, tokenKey, status, parentTokenId, errorMessage;
}
