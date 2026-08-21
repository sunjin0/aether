package com.aether.workflow.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.runtime.WorkflowSseHub;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.vo.BusinessWorkflowOptionVo;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 外部系统通过服务账号启动和查看工作流。
 */
@Api(tags = "业务工作流接入 API")
@RestController
@RequestMapping("/api/business/workflows")
public class BusinessWorkflowController {
    private final ServiceAccountService serviceAccountService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowExecutionService executionService;
    private final WorkflowSseHub sseHub;

    public BusinessWorkflowController(ServiceAccountService serviceAccountService,
                                      AgentWorkflowService workflowService,
                                      AgentWorkflowExecutionService executionService,
                                      WorkflowSseHub sseHub) {
        this.serviceAccountService = serviceAccountService;
        this.workflowService = workflowService;
        this.executionService = executionService;
        this.sseHub = sseHub;
    }

    @ApiOperation("查询当前服务账号可启动工作流")
    @GetMapping
    public WebResponse<List<BusinessWorkflowOptionVo>> workflows() {
        ServiceAccount account = currentAccount();
        List<String> allowed = parseIds(account.getAllowedWorkflowIds());
        List<AgentWorkflow> workflows = workflowService.list(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getDeleted, false)
                .eq(AgentWorkflow::getStatus, 1)
                .in(!allowed.isEmpty(), AgentWorkflow::getId, allowed)
                .orderByDesc(AgentWorkflow::getUpdatedAt));
        return WebResponse.OK(workflows.stream().map(this::toOption).collect(Collectors.toList()));
    }

    @ApiOperation("外部系统启动工作流")
    @PostMapping("/{workflowId}/instances")
    public WebResponse<String> start(@PathVariable String workflowId, @RequestBody AgentWorkflowBusinessStartDto dto) {
        String serviceAccountId = currentServiceAccountId();
        serviceAccountService.assertWorkflowStartAllowed(serviceAccountId, workflowId);
        AgentWorkflowInstance instance = executionService.startBusiness(workflowId, dto, currentPrincipalId());
        return WebResponse.OK(instance.getId());
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

    private BusinessWorkflowOptionVo toOption(AgentWorkflow workflow) {
        BusinessWorkflowOptionVo vo = new BusinessWorkflowOptionVo();
        vo.setId(workflow.getId());
        vo.setName(workflow.getName());
        vo.setDescription(workflow.getDescription());
        vo.setPublishedVersion(workflow.getPublishedVersion());
        return vo;
    }

    private ServiceAccount currentAccount() {
        ServiceAccount account = serviceAccountService.getById(currentServiceAccountId());
        if (account == null || Boolean.TRUE.equals(account.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("service-account.not-found"));
        return account;
    }

    private List<String> parseIds(String json) {
        if (StringUtils.isBlank(json)) return Collections.emptyList();
        return JSON.parseArray(json, String.class);
    }

    private String currentServiceAccountId() {
        Map<String, String> user = CurrentUser.getUser();
        String serviceAccountId = user == null ? null : user.get("serviceAccountId");
        if (StringUtils.isBlank(serviceAccountId))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return serviceAccountId;
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
