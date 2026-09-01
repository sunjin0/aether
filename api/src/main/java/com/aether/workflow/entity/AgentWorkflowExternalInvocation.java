package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流外部副作用调用记录。每个节点和幂等键只保留一条记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_external_invocation")
public class AgentWorkflowExternalInvocation extends BaseEntity {
    private String tenantId;
    private String applicationId;
    private String instanceId;
    private String nodeInstanceId;
    private String nodeId;
    private String invocationType;
    private String idempotencyKey;
    private String method;
    private String url;
    private String requestData;
    private String responseData;
    /** RECORDED / RUNNING / COMPLETED / FAILED / UNKNOWN */
    private String status;
    private String errorMessage;
    private Long startedAt;
    private Long completedAt;
}
