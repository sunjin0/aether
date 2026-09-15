package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowCapability;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentWorkflowCapabilityService extends IService<AgentWorkflowCapability> {
    List<AgentWorkflowCapability> listEnabledForAgent(String agentDefinitionId, String applicationId);
}
