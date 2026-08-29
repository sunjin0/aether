package com.aether.workflow.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.entity.ServiceAccount;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.workflow.dto.AgentWorkflowAnswerInstanceRequest;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowInteractionDto;
import com.aether.workflow.dto.AgentWorkflowEventDto;
import com.aether.workflow.dto.BusinessWorkflowStartInstanceRequest;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.runtime.WorkflowOutputResolver;
import com.aether.workflow.runtime.WorkflowSseHub;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.vo.BusinessWorkflowOptionVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;

/**
 * 外部系统通过服务账号启动和查看工作流。
 */
@Api(tags = "业务工作流接入 API")
@RestController
@RequestMapping("/api/business/workflows")
public class BusinessWorkflowController {
    private final ServiceAccountService serviceAccountService;
    private final AgentProductProfileService productProfileService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService workflowVersionService;
    private final AgentWorkflowExecutionService executionService;
    private final WorkflowSseHub sseHub;
    private final WorkflowOutputResolver outputResolver;

    public BusinessWorkflowController(ServiceAccountService serviceAccountService,
                                      AgentProductProfileService productProfileService,
                                      AgentWorkflowService workflowService,
                                      AgentWorkflowVersionService workflowVersionService,
                                      AgentWorkflowExecutionService executionService,
                                      WorkflowSseHub sseHub,
                                      WorkflowOutputResolver outputResolver) {
        this.serviceAccountService = serviceAccountService;
        this.productProfileService = productProfileService;
        this.workflowService = workflowService;
        this.workflowVersionService = workflowVersionService;
        this.executionService = executionService;
        this.sseHub = sseHub;
        this.outputResolver = outputResolver;
    }

    @ApiOperation("查询当前服务账号可启动工作流")
    @GetMapping
    public WebResponse<List<BusinessWorkflowOptionVo>> workflows() {
        ServiceAccount account = currentAccount();
        List<AgentProductProfile> products = allowedProducts(account);
        if (products.isEmpty()) return WebResponse.OK(Collections.<BusinessWorkflowOptionVo>emptyList());
        List<String> workflowIds = products.stream().map(AgentProductProfile::getWorkflowId)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        if (workflowIds.isEmpty()) return WebResponse.OK(Collections.<BusinessWorkflowOptionVo>emptyList());
        List<AgentWorkflow> workflows = workflowService.list(Wrappers.lambdaQuery(AgentWorkflow.class)
                .in(AgentWorkflow::getId, workflowIds).eq(AgentWorkflow::getApplicationId, account.getApplicationId())
                .eq(AgentWorkflow::getStatus, 1).eq(AgentWorkflow::getDeleted, false).orderByAsc(AgentWorkflow::getName));
        Map<String, AgentWorkflowVersion> versions = workflowVersionService.list(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .in(AgentWorkflowVersion::getWorkflowId, workflowIds).eq(AgentWorkflowVersion::getDeleted, false)).stream()
                .collect(Collectors.toMap(version -> versionKey(version.getWorkflowId(), version.getVersionNo()), version -> version,
                        (first, ignored) -> first, HashMap::new));
        return WebResponse.OK(workflows.stream().map(workflow -> toOption(workflow,
                versions.get(versionKey(workflow.getId(), workflow.getPublishedVersion())))).collect(Collectors.toList()));
    }

    @ApiOperation("外部系统启动工作流")
    @PostMapping("/{workflowId}/instances")
    public WebResponse<String> start(@PathVariable String workflowId, @RequestBody BusinessWorkflowStartInstanceRequest request) {
        String serviceAccountId = currentServiceAccountId();
        serviceAccountService.assertWorkflowStartAllowed(serviceAccountId, workflowId);
        AgentWorkflowInstance instance = executionService.startBusiness(workflowId, toBusinessStartDto(request), currentPrincipalId());
        return WebResponse.OK(null, instance.getId());
    }

    @ApiOperation("外部系统查看工作流实例")
    @GetMapping("/instances/{instanceId}")
    public WebResponse<AgentWorkflowInstanceVo> detail(@PathVariable String instanceId) {
        return WebResponse.OK(executionService.detail(instanceId, currentPrincipalId()));
    }

    @ApiOperation("外部系统订阅工作流实例实时事件")
    @GetMapping(value = "/instances/{instanceId}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable String instanceId) {
        AgentWorkflowInstanceVo snapshot = executionService.detail(instanceId, currentPrincipalId());
        SseEmitter emitter = sseHub.subscribe(instanceId);
        sseHub.publish(instanceId, "instance.status", snapshot);
        return emitter;
    }

    @ApiOperation("提交人工节点回答或 MCP 确认")
    @PostMapping("/instances/{instanceId}/answer")
    public WebResponse<Void> answer(@PathVariable String instanceId,
                                    @RequestBody AgentWorkflowAnswerInstanceRequest request) {
        AgentWorkflowInteractionDto dto = new AgentWorkflowInteractionDto();
        dto.setAnswer(request == null ? null : request.getAnswer());
        executionService.answer(instanceId, dto, currentPrincipalId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("提交业务事件并恢复等待节点")
    @PostMapping("/instances/{instanceId}/events/{eventType}")
    public WebResponse<Void> signalEvent(@PathVariable String instanceId, @PathVariable String eventType,
                                         @RequestBody(required = false) AgentWorkflowEventDto request) {
        executionService.signalEvent(instanceId, eventType, request, currentPrincipalId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("获取工作流实例当前返回值")
    @GetMapping("/instances/{instanceId}/result")
    public WebResponse<Map<String, Object>> result(@PathVariable String instanceId) {
        AgentWorkflowInstanceVo instance = executionService.detail(instanceId, currentPrincipalId());
        return WebResponse.OK(outputResolver.resolve(instance));
    }

    private String currentServiceAccountId() {
        Map<String, String> user = CurrentUser.getUser();
        String serviceAccountId = user == null ? null : user.get("serviceAccountId");
        if (StringUtils.isBlank(serviceAccountId))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return serviceAccountId;
    }

    private AgentWorkflowBusinessStartDto toBusinessStartDto(BusinessWorkflowStartInstanceRequest request) {
        if (request == null) return null;
        AgentWorkflowBusinessStartDto dto = new AgentWorkflowBusinessStartDto();
        BeanUtils.copyProperties(request, dto);
        return dto;
    }

    private BusinessWorkflowOptionVo toOption(AgentWorkflow workflow, AgentWorkflowVersion version) {
        BusinessWorkflowOptionVo value = new BusinessWorkflowOptionVo();
        value.setId(workflow.getId()); value.setName(workflow.getName()); value.setDescription(workflow.getDescription());
        value.setPublishedVersion(workflow.getPublishedVersion());
        if (version != null) {
            value.setInputSchema(version.getInputSchema());
            value.setOutputSchema(version.getOutputSchema());
        }
        return value;
    }

    private String versionKey(String workflowId, Integer versionNo) {
        return workflowId + ":" + versionNo;
    }

    private ServiceAccount currentAccount() {
        ServiceAccount account = serviceAccountService.getById(currentServiceAccountId());
        if (account == null || Boolean.TRUE.equals(account.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("service-account.not-found"));
        if (!Boolean.TRUE.equals(account.getEnabled()))
            throw new ServerException(403, I18nUtils.getMessage("service-account.disabled"));
        return account;
    }

    private List<AgentProductProfile> allowedProducts(ServiceAccount account) {
        if (StringUtils.isBlank(account.getAllowedProductIds())) return Collections.emptyList();
        List<String> ids;
        try { ids = com.alibaba.fastjson2.JSON.parseArray(account.getAllowedProductIds(), String.class); } catch (RuntimeException ex) { return Collections.emptyList(); }
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return productProfileService.list(Wrappers.lambdaQuery(AgentProductProfile.class).in(AgentProductProfile::getId, ids)
                .eq(AgentProductProfile::getApplicationId, account.getApplicationId()).eq(AgentProductProfile::getProductType, "WORKFLOW")
                .eq(AgentProductProfile::getStatus, 1).eq(AgentProductProfile::getDeleted, false));
    }

    private String currentPrincipalId() {
        Map<String, String> user = CurrentUser.getUser();
        String principalId = user == null ? null : user.get("principalId");
        if (StringUtils.isBlank(principalId)) principalId = user == null ? null : user.get("userId");
        if (StringUtils.isBlank(principalId))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return principalId;
    }
}
