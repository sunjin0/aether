package com.aether.workflow.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 通用业务事件去重收据。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_event_receipt")
public class AgentWorkflowEventReceipt extends AccountOwnedEntity {
    private String applicationId, eventType, eventId, correlationKey;
}
