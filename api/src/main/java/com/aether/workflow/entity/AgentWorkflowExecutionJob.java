package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流异步推进任务；实例状态仍是唯一事实来源。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_execution_job")
public class AgentWorkflowExecutionJob extends BaseEntity {
    private String instanceId;
    /** PENDING / PROCESSING / COMPLETED */
    private String status;
    private Integer attemptCount;
    private Long nextAttemptAt;
    private Long lockedAt;
    private String errorMessage;
    private Long completedAt;
}
