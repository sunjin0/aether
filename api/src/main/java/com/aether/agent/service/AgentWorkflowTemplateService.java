package com.aether.agent.service;
import com.aether.agent.entity.AgentWorkflow;
import com.aether.agent.entity.AgentWorkflowTemplate;
import com.baomidou.mybatisplus.extension.service.IService;
public interface AgentWorkflowTemplateService extends IService<AgentWorkflowTemplate> {
    AgentWorkflowTemplate createFromWorkflow(AgentWorkflow workflow, String name, String description);
    AgentWorkflow instantiate(String templateId, String name, String description);
}
