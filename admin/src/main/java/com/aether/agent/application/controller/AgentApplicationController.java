package com.aether.agent.application.controller;

import com.aether.agent.application.dto.AgentApplicationDto;
import com.aether.agent.dto.AgentControllerRequests.ApplicationList;
import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.agent.application.vo.AgentApplicationVo;
import com.aether.agent.application.vo.AgentApplicationUsageVo;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentRunService;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.entity.WebResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** 面向外部业务系统的 Agent 应用空间管理。 */
@RestController
@Api(tags = "Agent 应用管理 API")
@RequestMapping("/api/agent/application")
public class AgentApplicationController {
    private final AgentApplicationService applicationService;
    private final AgentRunService agentRunService;
    private final AgentWorkflowInstanceService workflowInstanceService;
    private final AgentWorkflowCallbackDeliveryService callbackDeliveryService;
    private final ServiceAccountService serviceAccountService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentWorkflowService workflowService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AgentProductProfileService profileService;

    public AgentApplicationController(AgentApplicationService applicationService, AgentRunService agentRunService,
                                       AgentWorkflowInstanceService workflowInstanceService, AgentWorkflowCallbackDeliveryService callbackDeliveryService,
                                       ServiceAccountService serviceAccountService, AgentDefinitionService agentDefinitionService,
                                       AgentWorkflowService workflowService, KnowledgeBaseService knowledgeBaseService,
                                       AgentProductProfileService profileService) {
        this.applicationService = applicationService;
        this.agentRunService = agentRunService; this.workflowInstanceService = workflowInstanceService; this.callbackDeliveryService = callbackDeliveryService;
        this.serviceAccountService = serviceAccountService; this.agentDefinitionService = agentDefinitionService;
        this.workflowService = workflowService; this.knowledgeBaseService = knowledgeBaseService; this.profileService = profileService;
    }

    @ApiOperation("查询 Agent 应用列表")
    @PostMapping("/list")
    @Permission(path = "/agent/application")
    public WebResponse<List<AgentApplicationVo>> list(@RequestBody(required = false) ApplicationList query) {
        long current = query == null || query.getCurrent() == null ? 1L : query.getCurrent();
        long pageSize = query == null || query.getPageSize() == null ? 20L : Math.min(100L, query.getPageSize());
        Page<AgentApplication> page = applicationService.page(new Page<AgentApplication>(current, pageSize),
                Wrappers.lambdaQuery(AgentApplication.class).eq(AgentApplication::getDeleted, false)
                        .like(query != null && StringUtils.hasText(query.getName()), AgentApplication::getName, query == null ? null : query.getName())
                        .like(query != null && StringUtils.hasText(query.getCode()), AgentApplication::getCode, query == null ? null : query.getCode())
                        .eq(query != null && query.getStatus() != null, AgentApplication::getStatus, query == null ? null : query.getStatus())
                        .orderByDesc(AgentApplication::getCreatedAt));
        return WebResponse.Page(page.getRecords().stream().map(this::vo).collect(Collectors.toList()), page.getTotal());
    }

    @ApiOperation("创建 Agent 应用")
    @PostMapping
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    public WebResponse<Void> create(@RequestBody AgentApplicationDto dto) {
        validate(dto);
        if (applicationService.count(Wrappers.lambdaQuery(AgentApplication.class).eq(AgentApplication::getCode, dto.getCode())
                .eq(AgentApplication::getDeleted, false)) > 0) throw new ServerException(422, I18nUtils.getMessage("agent.application.code.exists"));
        AgentApplication entity = new AgentApplication();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus(1);
        applicationService.save(entity);
        return WebResponse.OK(I18nUtils.getMessage("agent.application.create.success"));
    }

    @ApiOperation("更新 Agent 应用")
    @PutMapping("/{id}")
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentApplicationDto dto) {
        AgentApplication entity = applicationService.getById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.application.not-found"));
        validate(dto);
        if (!entity.getCode().equals(dto.getCode()) && applicationService.count(Wrappers.lambdaQuery(AgentApplication.class)
                .eq(AgentApplication::getCode, dto.getCode()).eq(AgentApplication::getDeleted, false)) > 0)
            throw new ServerException(422, I18nUtils.getMessage("agent.application.code.exists"));
        BeanUtils.copyProperties(dto, entity);
        applicationService.updateById(entity);
        return WebResponse.OK(I18nUtils.getMessage("agent.application.update.success"));
    }

    @ApiOperation("删除 Agent 应用")
    @DeleteMapping("/{id}")
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    public WebResponse<Void> delete(@PathVariable String id) {
        AgentApplication entity = applicationService.getById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.application.not-found"));
        if ("0".equals(id)) throw new ServerException(422, I18nUtils.getMessage("agent.application.default.delete.forbidden"));
        if (hasReferences(id)) throw new ServerException(422, I18nUtils.getMessage("agent.application.delete.references.exist"));
        applicationService.removeById(id);
        return WebResponse.OK(I18nUtils.getMessage("agent.application.delete.success"));
    }

    @ApiOperation("查询 Agent 应用使用情况")
    @GetMapping("/{id}/usage")
    @Permission(path = "/agent/application")
    public WebResponse<AgentApplicationUsageVo> usage(@PathVariable String id) {
        applicationService.requireActive(id);
        AgentApplicationUsageVo value = new AgentApplicationUsageVo(); value.setApplicationId(id);
        java.util.List<AgentRun> runs = agentRunService.list(Wrappers.lambdaQuery(AgentRun.class).eq(AgentRun::getApplicationId, id).eq(AgentRun::getDeleted, false));
        value.setAgentRuns((long) runs.size()); value.setTotalTokens(runs.stream().mapToLong(item -> item.getTotalTokens() == null ? 0L : item.getTotalTokens()).sum());
        value.setWorkflowRuns(workflowInstanceService.count(Wrappers.lambdaQuery(AgentWorkflowInstance.class).eq(AgentWorkflowInstance::getApplicationId, id).eq(AgentWorkflowInstance::getDeleted, false)));
        value.setCallbackFailed(callbackDeliveryService.count(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class).eq(AgentWorkflowCallbackDelivery::getApplicationId, id).eq(AgentWorkflowCallbackDelivery::getStatus, "FAILED").eq(AgentWorkflowCallbackDelivery::getDeleted, false)));
        return WebResponse.OK(value);
    }

    private void validate(AgentApplicationDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getCode()) || !StringUtils.hasText(dto.getName()))
            throw new ServerException(422, I18nUtils.getMessage("agent.application.code-name.required"));
        if (!dto.getCode().matches("[A-Za-z0-9_-]{2,64}")) throw new ServerException(422, I18nUtils.getMessage("agent.application.code.invalid"));
        if ((dto.getMaxAgentCallsPerHour() != null && (dto.getMaxAgentCallsPerHour() < 0 || dto.getMaxAgentCallsPerHour() > 100000))
                || (dto.getMaxWorkflowStartsPerHour() != null && (dto.getMaxWorkflowStartsPerHour() < 0 || dto.getMaxWorkflowStartsPerHour() > 100000)))
            throw new ServerException(422, I18nUtils.getMessage("agent.application.quota.invalid"));
    }

    private boolean hasReferences(String applicationId) {
        return serviceAccountService.count(Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getApplicationId, applicationId).eq(ServiceAccount::getDeleted, false)) > 0
                || agentDefinitionService.count(Wrappers.lambdaQuery(AgentDefinition.class).eq(AgentDefinition::getApplicationId, applicationId).eq(AgentDefinition::getDeleted, false)) > 0
                || workflowService.count(Wrappers.lambdaQuery(AgentWorkflow.class).eq(AgentWorkflow::getApplicationId, applicationId).eq(AgentWorkflow::getDeleted, false)) > 0
                || knowledgeBaseService.count(Wrappers.lambdaQuery(KnowledgeBase.class).eq(KnowledgeBase::getApplicationId, applicationId).eq(KnowledgeBase::getDeleted, false)) > 0
                || profileService.count(Wrappers.lambdaQuery(AgentProductProfile.class).eq(AgentProductProfile::getApplicationId, applicationId).eq(AgentProductProfile::getDeleted, false)) > 0
                || agentRunService.count(Wrappers.lambdaQuery(AgentRun.class).eq(AgentRun::getApplicationId, applicationId).eq(AgentRun::getDeleted, false)) > 0
                || workflowInstanceService.count(Wrappers.lambdaQuery(AgentWorkflowInstance.class).eq(AgentWorkflowInstance::getApplicationId, applicationId).eq(AgentWorkflowInstance::getDeleted, false)) > 0;
    }

    private AgentApplicationVo vo(AgentApplication source) {
        AgentApplicationVo result = new AgentApplicationVo();
        BeanUtils.copyProperties(source, result);
        return result;
    }
}
