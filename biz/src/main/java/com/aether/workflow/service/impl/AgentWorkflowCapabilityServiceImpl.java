package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentDefinitionWorkflowCapabilityBinding;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.mapper.AgentWorkflowCapabilityMapper;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/** 查询启用且明确授予 Agent 的工作流能力。 */
@Service
public class AgentWorkflowCapabilityServiceImpl extends ServiceImpl<AgentWorkflowCapabilityMapper, AgentWorkflowCapability>
        implements AgentWorkflowCapabilityService {

    private final AgentDefinitionWorkflowCapabilityBindingService bindingService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;

    public AgentWorkflowCapabilityServiceImpl(AgentDefinitionWorkflowCapabilityBindingService bindingService,
                                              AgentWorkflowService workflowService,
                                              AgentWorkflowVersionService versionService) {
        this.bindingService = bindingService;
        this.workflowService = workflowService;
        this.versionService = versionService;
    }

    @Override
    public List<AgentWorkflowCapability> listEnabledForAgent(String agentDefinitionId, String applicationId) {
        if (StringUtils.isBlank(agentDefinitionId) || StringUtils.isBlank(applicationId)) return Collections.emptyList();
        List<String> boundIds = bindingService.list(Wrappers.lambdaQuery(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentDefinitionId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getStatus, 1)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getDeleted, false)
                .orderByAsc(AgentDefinitionWorkflowCapabilityBinding::getPriority))
                .stream().map(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId).collect(java.util.stream.Collectors.toList());
        if (boundIds.isEmpty()) return Collections.emptyList();
        List<AgentWorkflowCapability> candidates = list(Wrappers.lambdaQuery(AgentWorkflowCapability.class)
                .eq(AgentWorkflowCapability::getApplicationId, applicationId)
                .eq(AgentWorkflowCapability::getEnabled, true)
                .eq(AgentWorkflowCapability::getStatus, 1)
                .in(AgentWorkflowCapability::getId, boundIds)
                .eq(AgentWorkflowCapability::getDeleted, false)
                .orderByAsc(AgentWorkflowCapability::getCapabilityCode));
        java.util.ArrayList<AgentWorkflowCapability> result = new java.util.ArrayList<>();
        for (AgentWorkflowCapability candidate : candidates) {
            if (candidate == null || StringUtils.isBlank(candidate.getWorkflowId())
                    || StringUtils.isBlank(candidate.getWorkflowVersionId())) continue;
            AgentWorkflow workflow = workflowService.getById(candidate.getWorkflowId());
            if (workflow == null || !Integer.valueOf(1).equals(workflow.getStatus())
                    || workflow.getPublishedVersion() == null) continue;
            AgentWorkflowVersion published = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                    .eq(AgentWorkflowVersion::getWorkflowId, workflow.getId())
                    .eq(AgentWorkflowVersion::getVersionNo, workflow.getPublishedVersion())
                    .eq(AgentWorkflowVersion::getDeleted, false));
            // 能力只能暴露当前发布快照；旧版本绑定由启动前校验兜底，但不再进入模型上下文。
            if (published != null && StringUtils.equals(published.getId(), candidate.getWorkflowVersionId())) {
                result.add(candidate);
            }
        }
        return result;
    }
}
