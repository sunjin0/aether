package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowTemplate;
import com.aether.workflow.mapper.AgentWorkflowTemplateMapper;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowTemplateService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现智能体工作流Template业务服务。
 */
@Service
public class AgentWorkflowTemplateServiceImpl extends ServiceImpl<AgentWorkflowTemplateMapper, AgentWorkflowTemplate>
        implements AgentWorkflowTemplateService {
    private final AgentWorkflowService workflowService;

    /**
     * 创建 {@code AgentWorkflowTemplateServiceImpl} 实例。
     */
    public AgentWorkflowTemplateServiceImpl(AgentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * 创建From工作流。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowTemplate createFromWorkflow(AgentWorkflow workflow, String name, String description) {
        if (workflow == null) throw new ServerException(404, I18nUtils.getMessage("workflow.not-found"));
        if (StringUtils.isBlank(name))
            throw new ServerException(422, I18nUtils.getMessage("workflow.template.name.required"));
        AgentWorkflowTemplate template = new AgentWorkflowTemplate();
        template.setName(name);
        template.setDescription(StringUtils.abbreviate(description, 1024));
        template.setAgentDefinitionId(workflow.getAgentDefinitionId());
        template.setNodes(workflow.getNodes());
        template.setEdges(workflow.getEdges());
        template.setInputSchema(StringUtils.defaultIfBlank(workflow.getInputSchema(), "[]"));
        template.setOutputSchema(StringUtils.defaultIfBlank(workflow.getOutputSchema(), "[]"));
        template.setSourceWorkflowId(workflow.getId());
        template.setSourceVersion(workflow.getPublishedVersion());
        save(template);
        return template;
    }

    /**
     * 处理instantiate。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflow instantiate(String templateId, String name, String description) {
        AgentWorkflowTemplate template = getById(templateId);
        if (template == null || Boolean.TRUE.equals(template.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.template.not-found"));
        if (StringUtils.isBlank(name))
            throw new ServerException(422, I18nUtils.getMessage("workflow.template.new-workflow-name.required"));
        AgentWorkflow workflow = new AgentWorkflow();
        workflow.setName(name);
        workflow.setDescription(StringUtils.abbreviate(description, 1024));
        workflow.setAgentDefinitionId(template.getAgentDefinitionId());
        workflow.setNodes(template.getNodes());
        workflow.setEdges(template.getEdges());
        workflow.setInputSchema(template.getInputSchema());
        workflow.setOutputSchema(template.getOutputSchema());
        workflow.setStatus(0);
        workflowService.save(workflow);
        return workflow;
    }
}
