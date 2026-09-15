package com.aether.workflow.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.workflow.dto.AgentWorkflowCapabilityRequest;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.entity.AgentDefinitionWorkflowCapabilityBinding;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.application.service.AgentApplicationService;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Agent 可调用工作流能力管理接口。 */
@Api(tags = "Agent 工作流能力 API")
@RestController
@Permission(path = "/agent/workflow-capability")
@RequestMapping("/api/agent/workflow-capability")
public class AgentWorkflowCapabilityController {
    private final AgentWorkflowCapabilityService capabilityService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;
    private final AgentToolCatalog toolCatalog;
    private final AgentApplicationService applicationService;
    private final AgentDefinitionWorkflowCapabilityBindingService capabilityBindingService;

    public AgentWorkflowCapabilityController(AgentWorkflowCapabilityService capabilityService,
                                             AgentWorkflowService workflowService,
                                             AgentWorkflowVersionService versionService,
                                             AgentToolCatalog toolCatalog,
                                             AgentApplicationService applicationService,
                                             AgentDefinitionWorkflowCapabilityBindingService capabilityBindingService) {
        this.capabilityService = capabilityService;
        this.workflowService = workflowService;
        this.versionService = versionService;
        this.toolCatalog = toolCatalog;
        this.applicationService = applicationService;
        this.capabilityBindingService = capabilityBindingService;
    }

    @ApiOperation("查询工作流能力")
    @GetMapping
    public WebResponse<List<AgentWorkflowCapability>> list(@RequestParam(required = false) String applicationId,
                                                           @RequestParam(required = false) String workflowId) {
        return WebResponse.OK(capabilityService.list(Wrappers.lambdaQuery(AgentWorkflowCapability.class)
                .eq(StringUtils.isNotBlank(applicationId), AgentWorkflowCapability::getApplicationId, applicationId)
                .eq(StringUtils.isNotBlank(workflowId), AgentWorkflowCapability::getWorkflowId, workflowId)
                .eq(StringUtils.isNotBlank(currentTenantId()), AgentWorkflowCapability::getTenantId, currentTenantId())
                .eq(AgentWorkflowCapability::getDeleted, false)
                .orderByDesc(AgentWorkflowCapability::getUpdatedAt)));
    }

    @ApiOperation("创建工作流能力")
    @Permission(path = "/agent/workflow-capability", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> create(@RequestBody AgentWorkflowCapabilityRequest request) {
        if (request != null && StringUtils.isBlank(request.getAllowedActions())) request.setAllowedActions("[\"START\",\"OBSERVE\"]");
        validateRequest(request);
        applyVersionSchemas(request);
        AgentWorkflowCapability value = new AgentWorkflowCapability();
        BeanUtils.copyProperties(request, value);
        value.setTenantId(currentTenantId());
        value.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        // enabled=true is the explicit publish switch; keep status in sync so a
        // newly created enabled capability is immediately visible to the tool catalog.
        value.setStatus(Boolean.TRUE.equals(request.getEnabled())
                ? 1 : (request.getStatus() == null ? 0 : request.getStatus()));
        if (capabilityService.count(Wrappers.lambdaQuery(AgentWorkflowCapability.class)
                .eq(AgentWorkflowCapability::getApplicationId, request.getApplicationId())
                .eq(AgentWorkflowCapability::getCapabilityCode, request.getCapabilityCode())
                .eq(AgentWorkflowCapability::getDeleted, false)) > 0)
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.code.exists"));
        capabilityService.save(value);
        return WebResponse.OK(I18nUtils.getMessage("agent.workflow.capability.create.success"), value.getId());
    }

    @ApiOperation("更新工作流能力")
    @Permission(path = "/agent/workflow-capability", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentWorkflowCapabilityRequest request) {
        AgentWorkflowCapability current = required(id);
        if (request != null && StringUtils.isBlank(request.getAllowedActions())) request.setAllowedActions(current.getAllowedActions());
        validateRequest(request);
        boolean versionChanged = !StringUtils.equals(current.getWorkflowId(), request.getWorkflowId())
                || !StringUtils.equals(current.getWorkflowVersionId(), request.getWorkflowVersionId());
        if (!versionChanged && StringUtils.isBlank(request.getInputSchema())) request.setInputSchema(current.getInputSchema());
        if (!versionChanged && StringUtils.isBlank(request.getOutputSchema())) request.setOutputSchema(current.getOutputSchema());
        if (!StringUtils.equals(current.getApplicationId(), request.getApplicationId()))
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.application.immutable"));
        applyVersionSchemas(request);
        if (!StringUtils.equals(current.getCapabilityCode(), request.getCapabilityCode())
                && capabilityService.count(Wrappers.lambdaQuery(AgentWorkflowCapability.class)
                .eq(AgentWorkflowCapability::getApplicationId, request.getApplicationId())
                .eq(AgentWorkflowCapability::getCapabilityCode, request.getCapabilityCode())
                .ne(AgentWorkflowCapability::getId, id)
                .eq(AgentWorkflowCapability::getDeleted, false)) > 0)
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.code.exists"));
        Boolean previousEnabled = current.getEnabled();
        Integer previousStatus = current.getStatus();
        BeanUtils.copyProperties(request, current);
        current.setId(id);
        current.setTenantId(currentTenantId());
        if (request.getEnabled() == null) current.setEnabled(previousEnabled);
        if (request.getStatus() == null) current.setStatus(request.getEnabled() != null
                ? (Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0)
                : (previousStatus == null ? (Boolean.TRUE.equals(current.getEnabled()) ? 1 : 0) : previousStatus));
        capabilityService.updateById(current);
        evictAgents(current.getId());
        return WebResponse.OK(I18nUtils.getMessage("agent.workflow.capability.update.success"));
    }

    @ApiOperation("删除工作流能力")
    @Permission(path = "/agent/workflow-capability", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable String id) {
        AgentWorkflowCapability value = required(id);
        // 能力删除与 Agent 绑定关系必须在同一事务内完成，避免产生悬挂绑定。
        evictAgents(id);
        capabilityBindingService.remove(Wrappers.lambdaUpdate(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId, id));
        capabilityService.removeById(value);
        return WebResponse.OK(I18nUtils.getMessage("agent.workflow.capability.delete.success"));
    }

    @ApiOperation("启用或停用工作流能力")
    @Permission(path = "/agent/workflow-capability", type = Permission.Type.Write)
    @PostMapping("/{id}/enabled")
    public WebResponse<Void> enabled(@PathVariable String id, @RequestParam boolean enabled) {
        AgentWorkflowCapability value = required(id);
        if (enabled) validatePublished(value);
        AgentWorkflowCapability update = new AgentWorkflowCapability();
        update.setId(id);
        update.setEnabled(enabled);
        update.setStatus(enabled ? 1 : 0);
        capabilityService.updateById(update);
        evictAgents(value.getId());
        return WebResponse.OK(I18nUtils.getMessage("agent.workflow.capability.status.success"));
    }

    private AgentWorkflowCapability required(String id) {
        AgentWorkflowCapability value = capabilityService.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("agent.workflow.capability.not-found"));
        if (StringUtils.isNotBlank(currentTenantId()) && !StringUtils.equals(currentTenantId(), value.getTenantId()))
            throw new ServerException(403, I18nUtils.getMessage("agent.workflow.capability.application.denied"));
        return value;
    }

    private void validateRequest(AgentWorkflowCapabilityRequest request) {
        if (request == null || StringUtils.isAnyBlank(request.getApplicationId(), request.getWorkflowId(),
                request.getWorkflowVersionId(), request.getCapabilityCode(), request.getDisplayName()))
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.capability.fields.required"));
        // Tool function names append `_start`/`_instance`; keep the capability
        // prefix within the provider's 64-character function-name limit.
        if (!request.getCapabilityCode().matches("[A-Za-z][A-Za-z0-9_-]{2,54}"))
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.capability.code.invalid"));
        applicationService.requireActive(request.getApplicationId());
        if (!validActions(request.getAllowedActions())
                || !optionalJsonArray(request.getAgentWritableVariables()) || !optionalJsonArray(request.getAllowedEventTypes()))
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.capability.policy.invalid"));
        AgentWorkflow workflow = workflowService.getById(request.getWorkflowId());
        AgentWorkflowVersion version = versionService.getById(request.getWorkflowVersionId());
        if (workflow == null || version == null || !request.getWorkflowId().equals(version.getWorkflowId()))
            throw new ServerException(404, I18nUtils.getMessage("agent.workflow.capability.version.invalid"));
        if (!StringUtils.equals(request.getApplicationId(), workflow.getApplicationId())
                || !tenantMatches(workflow.getTenantId()))
            throw new ServerException(403, I18nUtils.getMessage("agent.workflow.capability.application.denied"));
        if (!Integer.valueOf(1).equals(workflow.getStatus()) || workflow.getPublishedVersion() == null
                || !workflow.getPublishedVersion().equals(version.getVersionNo()))
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.version.invalid"));
    }

    private void validatePublished(AgentWorkflowCapability value) {
        AgentWorkflow workflow = workflowService.getById(value.getWorkflowId());
        AgentWorkflowVersion version = versionService.getById(value.getWorkflowVersionId());
        if (workflow == null || version == null || !StringUtils.equals(value.getApplicationId(), workflow.getApplicationId())
                || !tenantMatches(workflow.getTenantId()) || !Integer.valueOf(1).equals(workflow.getStatus())
                || !Integer.valueOf(version.getVersionNo()).equals(workflow.getPublishedVersion()))
            throw new ServerException(409, I18nUtils.getMessage("agent.workflow.capability.version.invalid"));
    }

    private void applyVersionSchemas(AgentWorkflowCapabilityRequest request) {
        AgentWorkflowVersion version = versionService.getById(request.getWorkflowVersionId());
        if (version == null) return;
        if (StringUtils.isBlank(request.getInputSchema())) request.setInputSchema(version.getInputSchema());
        if (StringUtils.isBlank(request.getOutputSchema())) request.setOutputSchema(version.getOutputSchema());
    }

    private boolean jsonArray(String value) {
        try { return StringUtils.isNotBlank(value) && JSONArray.parseArray(value) != null; }
        catch (Exception ignored) { return false; }
    }

    private boolean optionalJsonArray(String value) {
        return StringUtils.isBlank(value) || jsonArray(value);
    }

    private boolean validActions(String value) {
        if (!jsonArray(value)) return false;
        try {
            for (Object item : JSONArray.parseArray(value)) {
                String action = String.valueOf(item);
                if (!java.util.Arrays.asList("START", "OBSERVE", "STOP", "PROVIDE_AGENT_INPUT", "RESOLVE_MCP_APPROVAL", "SIGNAL_EVENT", "RETRY_NODE").contains(action)) return false;
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private void evictAgents(String capabilityId) {
        if (StringUtils.isBlank(capabilityId) || capabilityBindingService == null) return;
        capabilityBindingService.list(Wrappers.lambdaQuery(AgentDefinitionWorkflowCapabilityBinding.class)
                        .eq(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId, capabilityId)
                        .eq(AgentDefinitionWorkflowCapabilityBinding::getDeleted, false))
                .forEach(binding -> toolCatalog.evict(binding.getAgentDefinitionId()));
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    private boolean tenantMatches(String tenantId) {
        String current = currentTenantId();
        return StringUtils.isBlank(current) || StringUtils.isBlank(tenantId) || StringUtils.equals(current, tenantId);
    }
}
