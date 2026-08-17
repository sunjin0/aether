package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowWebhookTrigger;
import com.aether.workflow.dto.AgentWorkflowWebhookTriggerDto;
import com.aether.workflow.vo.AgentWorkflowWebhookTriggerSecretVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 管理工作流 Webhook 触发器的创建、密钥轮换、启停和签名触发。
 */
public interface AgentWorkflowWebhookTriggerService extends IService<AgentWorkflowWebhookTrigger> {
    /**
     * 创建工作流 Webhook 触发器，并仅在本次调用中返回签名密钥。
     */
    AgentWorkflowWebhookTriggerSecretVo create(AgentWorkflowWebhookTriggerDto dto);

    /**
     * 轮换 Webhook 签名密钥，并返回新密钥。
     */
    AgentWorkflowWebhookTriggerSecretVo rotateSecret(String id);

    /**
     * 启用或停用指定 Webhook 触发器。
     */
    boolean setEnabled(String id, boolean enabled);

    /**
     * 校验时间戳和签名后，以 Webhook 请求体及请求头变量启动工作流实例。
     */
    AgentWorkflowInstance trigger(String id, String timestamp, String signature, String rawBody, Map<String, String> headers);
}
