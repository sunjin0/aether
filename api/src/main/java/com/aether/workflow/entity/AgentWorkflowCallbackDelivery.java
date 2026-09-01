package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流终态回调的可重试投递记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_callback_delivery")
public class AgentWorkflowCallbackDelivery extends BaseEntity {
    private String tenantId;
    /** 关联运行的业务应用空间，供审计和重试范围隔离。 */
    private String applicationId;
    private String instanceId;
    /**
     * workflow.completed / workflow.failed / workflow.terminated
     */
    private String eventType;
    private String callbackUrl;
    /** 创建终态事件时捕获的 W3C Trace Context，异步投递及重试时复用。 */
    private String traceparent;
    private String payload;
    /**
     * PENDING / DELIVERED / RETRYING / FAILED
     */
    private String status;
    private Integer attemptCount;
    private Integer responseStatus;
    private String responseBody;
    private String errorMessage;
    private Long nextAttemptAt;
    private Long deliveredAt;
}
