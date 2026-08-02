package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 外部事件到工作流实例的 Webhook 触发器。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_webhook_trigger")
public class AgentWorkflowWebhookTrigger extends BaseEntity {
    private String workflowId;
    private String serviceAccountId;
    private String name;
    /** 业务类型固定值。 */
    private String businessType;
    /** $body.id / $header.X-Request-Id / 字面量。 */
    private String businessIdExpression;
    private String idempotencyKeyExpression;
    /** JSON 对象：流程变量名 → 取值表达式。 */
    private String variableMapping;
    /** AES 加密存储，仅创建或轮换时返回明文。 */
    private String signingSecret;
    private Boolean enabled;
    private Long lastTriggeredAt;
    private String lastErrorMessage;
}
