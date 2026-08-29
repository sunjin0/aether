package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流实例的追加式审计事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_audit_event")
public class AgentWorkflowAuditEvent extends BaseEntity {
    private String instanceId;
    private String nodeInstanceId;
    private String eventType;
    private String actorId;
    private String summary;
    private String data;
    private Long occurredAt;
}
