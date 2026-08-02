package com.aether.workflow.service;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowTemplate;
import com.baomidou.mybatisplus.extension.service.IService;
public interface AgentWorkflowTemplateService extends IService<AgentWorkflowTemplate> {
    AgentWorkflowTemplate createFromWorkflow(AgentWorkflow workflow, String name, String description);
    AgentWorkflow instantiate(String templateId, String name, String description);
}
