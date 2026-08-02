package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowTemplate;
import com.aether.workflow.mapper.AgentWorkflowTemplateMapper;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowTemplateService;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentWorkflowTemplateServiceImpl extends ServiceImpl<AgentWorkflowTemplateMapper, AgentWorkflowTemplate>
        implements AgentWorkflowTemplateService {
    private final AgentWorkflowService workflowService;
    public AgentWorkflowTemplateServiceImpl(AgentWorkflowService workflowService) { this.workflowService = workflowService; }

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowTemplate createFromWorkflow(AgentWorkflow workflow, String name, String description) {
        if (workflow == null) throw new ServerException(404, "工作流不存在");
        if (StringUtils.isBlank(name)) throw new ServerException(422, "模板名称不能为空");
        AgentWorkflowTemplate template = new AgentWorkflowTemplate();
        template.setName(name); template.setDescription(StringUtils.abbreviate(description, 1024));
        template.setAgentDefinitionId(workflow.getAgentDefinitionId()); template.setNodes(workflow.getNodes()); template.setEdges(workflow.getEdges());
        template.setInputSchema(StringUtils.defaultIfBlank(workflow.getInputSchema(), "[]")); template.setOutputSchema(StringUtils.defaultIfBlank(workflow.getOutputSchema(), "[]"));
        template.setSourceWorkflowId(workflow.getId()); template.setSourceVersion(workflow.getPublishedVersion()); save(template);
        return template;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentWorkflow instantiate(String templateId, String name, String description) {
        AgentWorkflowTemplate template = getById(templateId);
        if (template == null || Boolean.TRUE.equals(template.getDeleted())) throw new ServerException(404, "工作流模板不存在");
        if (StringUtils.isBlank(name)) throw new ServerException(422, "新工作流名称不能为空");
        AgentWorkflow workflow = new AgentWorkflow();
        workflow.setName(name); workflow.setDescription(StringUtils.abbreviate(description, 1024)); workflow.setAgentDefinitionId(template.getAgentDefinitionId());
        workflow.setNodes(template.getNodes()); workflow.setEdges(template.getEdges()); workflow.setInputSchema(template.getInputSchema()); workflow.setOutputSchema(template.getOutputSchema());
        workflow.setStatus(0); workflowService.save(workflow); return workflow;
    }
}
