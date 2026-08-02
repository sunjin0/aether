package com.aether.workflow.vo;

import lombok.Data;

/** 创建或轮换 Webhook 时一次性返回的签名密钥。 */
@Data
public class AgentWorkflowWebhookTriggerSecretVo {
    private String id;
    private String webhookUrl;
    private String signingSecret;
}
