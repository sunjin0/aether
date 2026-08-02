package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowWebhookTrigger;
import com.aether.workflow.dto.AgentWorkflowWebhookTriggerDto;
import com.aether.workflow.vo.AgentWorkflowWebhookTriggerSecretVo;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.Map;

public interface AgentWorkflowWebhookTriggerService extends IService<AgentWorkflowWebhookTrigger> {
    AgentWorkflowWebhookTriggerSecretVo create(AgentWorkflowWebhookTriggerDto dto);
    AgentWorkflowWebhookTriggerSecretVo rotateSecret(String id);
    boolean setEnabled(String id, boolean enabled);
    AgentWorkflowInstance trigger(String id, String timestamp, String signature, String rawBody, Map<String, String> headers);
}
