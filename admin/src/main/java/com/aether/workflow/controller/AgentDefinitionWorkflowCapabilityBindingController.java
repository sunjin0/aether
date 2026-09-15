package com.aether.workflow.controller;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.evaluation.entity.EvaluationPolicy;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.workflow.dto.AgentWorkflowCapabilityBindingRequest;
import com.aether.workflow.entity.AgentDefinitionWorkflowCapabilityBinding;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.vo.AgentDefinitionWorkflowCapabilityBindingVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Api(tags = "Agent 工作流能力绑定 API")
@RestController
@Permission(path = "/agent/definition")
@RequestMapping("/api/agent/definition")
public class AgentDefinitionWorkflowCapabilityBindingController {
    private final AgentDefinitionWorkflowCapabilityBindingService bindingService;
    private final AgentWorkflowCapabilityService capabilityService;
    private final AgentDefinitionService agentDefinitionService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EvaluationPolicyService evaluationPolicyService;

    public AgentDefinitionWorkflowCapabilityBindingController(AgentDefinitionWorkflowCapabilityBindingService bindingService,
                                                               AgentWorkflowCapabilityService capabilityService,
                                                               AgentDefinitionService agentDefinitionService) {
        this.bindingService = bindingService;
        this.capabilityService = capabilityService;
        this.agentDefinitionService = agentDefinitionService;
    }

    @ApiOperation("查询 Agent 已绑定工作流能力")
    @PostMapping("/{agentId}/workflow-capabilities/list")
    public WebResponse<List<AgentDefinitionWorkflowCapabilityBindingVo>> list(@PathVariable @NotBlank String agentId,
                                                                                @RequestBody(required = false) Map<String, Object> query) {
        AgentDefinition agent = requireAgent(agentId);
        String keyword = query == null ? null : String.valueOf(query.getOrDefault("keyword", ""));
        List<AgentDefinitionWorkflowCapabilityBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getDeleted, false)
                .orderByAsc(AgentDefinitionWorkflowCapabilityBinding::getPriority));
        List<String> ids = bindings.stream().map(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId).collect(Collectors.toList());
        Map<String, AgentWorkflowCapability> capabilities = ids.isEmpty() ? Collections.emptyMap() : capabilityService.listByIds(ids).stream()
                .filter(item -> StringUtils.equals(item.getApplicationId(), agent.getApplicationId()))
                .collect(Collectors.toMap(AgentWorkflowCapability::getId, item -> item, (left, right) -> left));
        List<AgentDefinitionWorkflowCapabilityBindingVo> records = bindings.stream().map(binding -> {
            AgentWorkflowCapability capability = capabilities.get(binding.getCapabilityId());
            if (capability == null || (StringUtils.isNotBlank(keyword) && !contains(capability, keyword))) return null;
            AgentDefinitionWorkflowCapabilityBindingVo vo = new AgentDefinitionWorkflowCapabilityBindingVo();
            BeanUtils.copyProperties(binding, vo);
            BeanUtils.copyProperties(capability, vo);
            vo.setCapabilityId(binding.getCapabilityId());
            vo.setPriority(binding.getPriority());
            vo.setStatus(binding.getStatus());
            return vo;
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        return WebResponse.Page(records, (long) records.size());
    }

    @ApiOperation("查询 Agent 可绑定工作流能力")
    @PostMapping("/{agentId}/workflow-capabilities/available")
    public WebResponse<List<AgentWorkflowCapability>> available(@PathVariable @NotBlank String agentId,
                                                                 @RequestBody(required = false) Map<String, Object> query) {
        AgentDefinition agent = requireAgent(agentId);
        List<String> boundIds = bindingService.list(Wrappers.lambdaQuery(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getDeleted, false))
                .stream().map(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId).collect(Collectors.toList());
        String keyword = query == null ? null : String.valueOf(query.getOrDefault("keyword", ""));
        List<AgentWorkflowCapability> records = capabilityService.list(Wrappers.lambdaQuery(AgentWorkflowCapability.class)
                .eq(AgentWorkflowCapability::getApplicationId, agent.getApplicationId())
                .eq(AgentWorkflowCapability::getEnabled, true).eq(AgentWorkflowCapability::getStatus, 1)
                .eq(AgentWorkflowCapability::getDeleted, false)
                .notIn(!boundIds.isEmpty(), AgentWorkflowCapability::getId, boundIds)
                .orderByAsc(AgentWorkflowCapability::getDisplayName));
        if (StringUtils.isBlank(keyword)) return WebResponse.Page(records, (long) records.size());
        List<AgentWorkflowCapability> filtered = records.stream().filter(item -> contains(item, keyword)).collect(Collectors.toList());
        return WebResponse.Page(filtered, (long) filtered.size());
    }

    @ApiOperation("绑定工作流能力")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @PostMapping("/{agentId}/workflow-capabilities")
    public WebResponse<Void> bind(@PathVariable @NotBlank String agentId, @RequestBody AgentWorkflowCapabilityBindingRequest request) {
        AgentDefinition agent = requireAgent(agentId);
        assertMutable(agentId);
        assertWorkflowCapabilityBindable(agent);
        AgentWorkflowCapability capability = request == null ? null : capabilityService.getById(request.getCapabilityId());
        if (capability == null || !StringUtils.equals(capability.getApplicationId(), agent.getApplicationId()) || !Boolean.TRUE.equals(capability.getEnabled()))
            throw new ServerException(404, I18nUtils.getMessage("agent.workflow.capability.not-found"));
        if (bindingService.count(Wrappers.lambdaQuery(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId, capability.getId())
                .eq(AgentDefinitionWorkflowCapabilityBinding::getDeleted, false)) > 0)
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.binding.exists"));
        AgentDefinitionWorkflowCapabilityBinding binding = new AgentDefinitionWorkflowCapabilityBinding();
        binding.setTenantId(currentTenantId()); binding.setAgentDefinitionId(agentId); binding.setCapabilityId(capability.getId());
        binding.setPriority(request.getPriority() == null ? 0 : request.getPriority()); binding.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        bindingService.save(binding);
        return WebResponse.OK(I18nUtils.getMessage("agent.workflow.capability.binding.create.success"));
    }

    @ApiOperation("解绑工作流能力")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @DeleteMapping("/{agentId}/workflow-capabilities/{capabilityId}")
    public WebResponse<Void> unbind(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String capabilityId) {
        requireAgent(agentId); assertMutable(agentId);
        boolean removed = bindingService.remove(Wrappers.lambdaUpdate(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId, capabilityId));
        return WebResponse.OK(I18nUtils.getMessage(removed ? "agent.workflow.capability.binding.delete.success" : "agent.workflow.capability.binding.delete.fail"));
    }

    @ApiOperation("更新工作流能力绑定状态")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @PutMapping("/{agentId}/workflow-capabilities/{capabilityId}/status")
    public WebResponse<Void> status(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String capabilityId,
                                    @RequestBody AgentWorkflowCapabilityBindingRequest request) {
        requireAgent(agentId); assertMutable(agentId);
        boolean updated = bindingService.update(Wrappers.lambdaUpdate(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId, capabilityId)
                .set(AgentDefinitionWorkflowCapabilityBinding::getStatus, request.getStatus()));
        return WebResponse.OK(I18nUtils.getMessage(updated ? "agent.workflow.capability.binding.status.success" : "agent.workflow.capability.binding.status.fail"));
    }

    private AgentDefinition requireAgent(String id) {
        AgentDefinition agent = agentDefinitionService.getById(id);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.definition.not.found"));
        String tenant = currentTenantId();
        if (StringUtils.isNotBlank(tenant) && !StringUtils.equals(tenant, agent.getTenantId())) throw new ServerException(403, I18nUtils.getMessage("agent.workflow.capability.application.denied"));
        return agent;
    }
    private boolean contains(AgentWorkflowCapability item, String keyword) {
        String key = keyword.toLowerCase();
        return StringUtils.defaultString(item.getDisplayName()).toLowerCase().contains(key)
                || StringUtils.defaultString(item.getCapabilityCode()).toLowerCase().contains(key)
                || StringUtils.defaultString(item.getDescription()).toLowerCase().contains(key);
    }
    /**
     * Deep 智能体的运行时不接入工作流，因此不允许新建绑定。
     * 解绑与停用不受限制：存量绑定仍需保留清理手段。
     */
    private void assertWorkflowCapabilityBindable(AgentDefinition agent) {
        if ("DEEP".equalsIgnoreCase(StringUtils.trim(agent.getExecutionMode())))
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.binding.deep.unsupported"));
    }

    private void assertMutable(String agentId) {
        if (evaluationPolicyService == null) return;
        EvaluationPolicy policy = evaluationPolicyService.getOne(Wrappers.lambdaQuery(EvaluationPolicy.class)
                .eq(EvaluationPolicy::getTargetType, "AGENT").eq(EvaluationPolicy::getTargetId, agentId).eq(EvaluationPolicy::getDeleted, false), false);
        if (policy != null && Boolean.TRUE.equals(policy.getRequired())) throw new ServerException(409, I18nUtils.getMessage("agent.evaluation.gate.configuration.locked"));
    }
    private String currentTenantId() { return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId"); }
}
