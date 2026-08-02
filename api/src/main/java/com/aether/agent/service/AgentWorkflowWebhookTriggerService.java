package com.aether.agent.service;

import com.aether.agent.entity.AgentWorkflowInstance;
import com.aether.agent.entity.AgentWorkflowWebhookTrigger;
import com.aether.agent.dto.AgentWorkflowWebhookTriggerDto;
import com.aether.agent.vo.AgentWorkflowWebhookTriggerSecretVo;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.Map;

public interface AgentWorkflowWebhookTriggerService extends IService<AgentWorkflowWebhookTrigger> {
    AgentWorkflowWebhookTriggerSecretVo create(AgentWorkflowWebhookTriggerDto dto);
    AgentWorkflowWebhookTriggerSecretVo rotateSecret(String id);
    boolean setEnabled(String id, boolean enabled);
    AgentWorkflowInstance trigger(String id, String timestamp, String signature, String rawBody, Map<String, String> headers);
}
