package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 管理工作流模板的创建与实例化。
 */
public interface AgentWorkflowTemplateService extends IService<AgentWorkflowTemplate> {
    /**
     * 根据工作流当前定义创建可复用模板。
     */
    AgentWorkflowTemplate createFromWorkflow(AgentWorkflow workflow, String name, String description);

    /**
     * 根据模板创建新的工作流草稿。
     */
    AgentWorkflow instantiate(String templateId, String code, String name, String description);
}
