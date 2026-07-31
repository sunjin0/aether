package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流一次运行实例。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_instance")
public class AgentWorkflowInstance extends BaseEntity {
    private String workflowId;
    private String workflowVersionId;
    private String userId;
    /** RUNNING / WAITING_USER / FAILED / COMPLETED / TERMINATED */
    private String status;
    private String variables;
    private String currentNodeId;
    private String errorMessage;
    private Long startedAt;
    private Long completedAt;
}
