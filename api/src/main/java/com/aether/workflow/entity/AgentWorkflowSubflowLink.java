package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 父工作流节点与固定版本子工作流实例的关联。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_subflow_link")
public class AgentWorkflowSubflowLink extends BaseEntity {
    private String tenantId;
    private String parentInstanceId, parentNodeId, childInstanceId;
    private String childWorkflowId, childWorkflowVersionId, status;
}
