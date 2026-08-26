package com.aether.openapi.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.aether.openapi.dto.OpenApiAgentChatDto;
import com.aether.openapi.dto.OpenApiWorkflowStartDto;
import com.aether.openapi.vo.OpenApiRunVo;
import com.aether.openapi.service.OpenApiIdempotencyService;
import com.aether.sys.service.ServiceAccountService;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * 仅供服务账号调用的版本化业务接入 API。
 * 输入和输出刻意采用小型安全契约，后台管理能力不得从此入口暴露。
 */
@RestController
@RequestMapping("/openapi/v1")
public class OpenApiController {
    private final ServiceAccountService accountService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowExecutionService executionService;
    private final AgentDefinitionService agentService;
    private final AgentChatService chatService;
    private final AgentProductProfileService profileService;
    private final OpenApiIdempotencyService idempotencyService;

    public OpenApiController(ServiceAccountService accountService, AgentWorkflowService workflowService,
                             AgentWorkflowExecutionService executionService, AgentDefinitionService agentService,
                             AgentChatService chatService, AgentProductProfileService profileService, OpenApiIdempotencyService idempotencyService) {
        this.accountService = accountService;
        this.workflowService = workflowService;
        this.executionService = executionService;
        this.agentService = agentService;
        this.chatService = chatService;
        this.profileService = profileService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/capabilities")
    public WebResponse<java.util.List<java.util.Map<String, Object>>> capabilities() {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (AgentProductProfile item : profileService.list(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(AgentProductProfile::getApplicationId, applicationId()).eq(AgentProductProfile::getStatus, 1)
                .eq(AgentProductProfile::getDeleted, false))) {
            java.util.Map<String, Object> value = new java.util.LinkedHashMap<String, Object>();
            value.put("name", item.getName()); value.put("productType", item.getProductType()); value.put("version", item.getVersionNo());
            value.put("inputSchema", item.getInputSchema()); value.put("outputSchema", item.getOutputSchema()); result.add(value);
        }
        return WebResponse.OK(result);
    }

    @PostMapping("/workflows/runs")
    public WebResponse<OpenApiRunVo> startWorkflow(@RequestBody OpenApiWorkflowStartDto request) {
        String applicationId = applicationId();
        if (request == null || StringUtils.isBlank(request.getWorkflowCode()) || StringUtils.isBlank(request.getBusinessId())
                || StringUtils.isBlank(request.getIdempotencyKey())) throw new ServerException(422, "workflowCode、businessId 和 idempotencyKey 不能为空");
        AgentWorkflow workflow = workflowService.getOne(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getApplicationId, applicationId).eq(AgentWorkflow::getCode, request.getWorkflowCode())
                .eq(AgentWorkflow::getDeleted, false));
        if (workflow == null || !Integer.valueOf(1).equals(workflow.getStatus())) throw new ServerException(404, "未找到已发布工作流");
        accountService.assertWorkflowStartAllowed(serviceAccountId(), workflow.getId());
        AgentWorkflowBusinessStartDto dto = new AgentWorkflowBusinessStartDto();
        dto.setBusinessId(request.getBusinessId()); dto.setBusinessType(request.getBusinessType());
        dto.setIdempotencyKey(request.getIdempotencyKey()); dto.setCallbackUrl(request.getCallbackUrl());
        dto.setDeadlineAt(request.getDeadlineAt()); dto.setVariables(request.getInput() == null ? Collections.<String, Object>emptyMap() : request.getInput());
        AgentWorkflowInstance instance = executionService.startBusiness(workflow.getId(), dto, CurrentUser.getUser().get("userId"));
        return WebResponse.OK(run(instance.getId(), instance.getBusinessId(), instance.getStatus()));
    }

    @GetMapping("/workflows/runs/{runId}")
    public WebResponse<OpenApiRunVo> workflowRun(@PathVariable String runId) {
        AgentWorkflowInstanceVo instance = executionService.detail(runId, CurrentUser.getUser().get("userId"));
        return WebResponse.OK(run(instance.getId(), instance.getBusinessId(), instance.getStatus()));
    }

    @PostMapping("/workflows/runs/{runId}/cancel")
    public WebResponse<OpenApiRunVo> cancelWorkflow(@PathVariable String runId) {
        executionService.terminate(runId, CurrentUser.getUser().get("userId"));
        AgentWorkflowInstanceVo instance = executionService.detail(runId, CurrentUser.getUser().get("userId"));
        return WebResponse.OK(run(instance.getId(), instance.getBusinessId(), instance.getStatus()));
    }

    @PostMapping("/agents/chat")
    public WebResponse<AgentMessageVo> chat(@RequestBody OpenApiAgentChatDto request) {
        if (request == null || StringUtils.isBlank(request.getAgentCode()) || StringUtils.isBlank(request.getInput()))
            throw new ServerException(422, "agentCode 和 input 不能为空");
        AgentDefinition agent = agentService.getOne(Wrappers.lambdaQuery(AgentDefinition.class)
                .eq(AgentDefinition::getApplicationId, applicationId()).eq(AgentDefinition::getCode, request.getAgentCode())
                .eq(AgentDefinition::getDeleted, false));
        if (agent == null) throw new ServerException(404, "未找到 Agent");
        accountService.assertAgentCallAllowed(serviceAccountId(), agent.getId());
        if ("DEEP".equalsIgnoreCase(agent.getExecutionMode())) throw new ServerException(422, "Deep Agent 请使用异步任务接口");
        if (StringUtils.isBlank(request.getIdempotencyKey())) throw new ServerException(422, "idempotencyKey 不能为空");
        AgentMessageVo response = idempotencyService.execute(applicationId() + ":agent:" + agent.getId(), request.getIdempotencyKey(), AgentMessageVo.class, () -> {
            AgentChatDto dto = new AgentChatDto();
            dto.setAgentId(agent.getId()); dto.setConversationId(request.getConversationId()); dto.setMessage(request.getInput());
            dto.setRequestId(request.getIdempotencyKey());
            return chatService.chat(dto);
        });
        return WebResponse.OK(response);
    }

    private OpenApiRunVo run(String runId, String businessId, String status) {
        OpenApiRunVo value = new OpenApiRunVo(); value.setRunId(runId); value.setBusinessId(businessId); value.setStatus(status);
        value.setTraceId(MDC.get("traceId")); return value;
    }
    private String serviceAccountId() {
        Map<String, String> user = CurrentUser.getUser();
        if (user == null || StringUtils.isBlank(user.get("serviceAccountId"))) throw new ServerException(401, "服务账号令牌无效");
        return user.get("serviceAccountId");
    }
    private String applicationId() {
        Map<String, String> user = CurrentUser.getUser();
        return user == null ? "0" : StringUtils.defaultIfBlank(user.get("applicationId"), "0");
    }
}
