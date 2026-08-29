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

import java.util.UUID;

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
    public AgentWorkflow instantiate(String templateId, String code, String name, String description) {
        AgentWorkflowTemplate template = getById(templateId);
        if (template == null || Boolean.TRUE.equals(template.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.template.not-found"));
        if (StringUtils.isBlank(name))
            throw new ServerException(422, I18nUtils.getMessage("workflow.template.new-workflow-name.required"));
        AgentWorkflow workflow = new AgentWorkflow();
        AgentWorkflow source = StringUtils.isBlank(template.getSourceWorkflowId()) ? null : workflowService.getById(template.getSourceWorkflowId());
        String applicationId = source == null ? "0" : StringUtils.defaultIfBlank(source.getApplicationId(), "0");
        workflow.setApplicationId(applicationId);
        workflow.setCode(resolveCode(applicationId, code));
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

    /**
     * 模板实例化是创建新工作流，必须给业务调用端分配独立且合法的编码。
     */
    private String resolveCode(String applicationId, String requestedCode) {
        if (StringUtils.isNotBlank(requestedCode)) {
            if (!requestedCode.matches("[A-Za-z][A-Za-z0-9_-]{2,63}"))
                throw new ServerException(422, "工作流编码必须为 3-64 位字母、数字、下划线或短横线");
            if (workflowService.count(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(AgentWorkflow.class)
                    .eq(AgentWorkflow::getApplicationId, applicationId).eq(AgentWorkflow::getCode, requestedCode)
                    .eq(AgentWorkflow::getDeleted, false)) > 0)
                throw new ServerException(422, "工作流编码已存在");
            return requestedCode;
        }
        for (int i = 0; i < 3; i++) {
            String generated = "wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            if (workflowService.count(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(AgentWorkflow.class)
                    .eq(AgentWorkflow::getApplicationId, applicationId).eq(AgentWorkflow::getCode, generated)
                    .eq(AgentWorkflow::getDeleted, false)) == 0) return generated;
        }
        throw new ServerException(409, "工作流编码生成冲突，请重试");
    }
}
