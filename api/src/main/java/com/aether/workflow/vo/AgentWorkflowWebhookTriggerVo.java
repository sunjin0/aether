package com.aether.workflow.vo;

import com.aether.workflow.entity.AgentWorkflowWebhookTrigger;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Webhook 管理视图；不返回签名密钥。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentWorkflowWebhookTriggerVo extends AgentWorkflowWebhookTrigger {
    private Long current;
    private Long pageSize;
}
