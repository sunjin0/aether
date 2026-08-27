package com.aether.openapi.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.vo.AgentMessageVo;
import com.alibaba.fastjson2.JSON;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.aether.openapi.dto.OpenApiAgentChatDto;
import com.aether.openapi.dto.OpenApiAgentRunStartDto;
import com.aether.openapi.dto.OpenApiWorkflowStartDto;
import com.aether.openapi.vo.OpenApiRunVo;
import com.aether.openapi.vo.OpenApiAgentChatVo;
import com.aether.openapi.vo.OpenApiAgentRunVo;
import com.aether.openapi.service.OpenApiIdempotencyService;
import com.aether.sys.service.ServiceAccountService;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * 仅供服务账号调用的版本化业务接入 API。
 * 输入和输出刻意采用小型安全契约，后台管理能力不得从此入口暴露。
 */
@RestController
@Api(tags = "开放业务接入 API")
@RequestMapping("/openapi/v1")
public class OpenApiController {
    private final ServiceAccountService accountService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowExecutionService executionService;
    private final AgentDefinitionService agentService;
    private final AgentChatService chatService;
    private final AgentRunService agentRunService;
    private final AgentConversationService conversationService;
    private final DeepAgentRunService deepAgentRunService;
    private final ThreadPoolTaskExecutor executor;
    private final AgentProductProfileService profileService;
    private final OpenApiIdempotencyService idempotencyService;

    public OpenApiController(ServiceAccountService accountService, AgentWorkflowService workflowService,
                              AgentWorkflowExecutionService executionService, AgentDefinitionService agentService,
                              AgentChatService chatService, AgentRunService agentRunService,
                              AgentConversationService conversationService, DeepAgentRunService deepAgentRunService,
                              @Qualifier("asyncPoolTaskExecutor") ThreadPoolTaskExecutor executor,
                              AgentProductProfileService profileService, OpenApiIdempotencyService idempotencyService) {
        this.accountService = accountService;
        this.workflowService = workflowService;
        this.executionService = executionService;
        this.agentService = agentService;
        this.chatService = chatService;
        this.agentRunService = agentRunService;
        this.conversationService = conversationService;
        this.deepAgentRunService = deepAgentRunService;
        this.executor = executor;
        this.profileService = profileService;
        this.idempotencyService = idempotencyService;
    }

    @ApiOperation("查询当前服务账号可用能力")
    @GetMapping("/capabilities")
    public WebResponse<java.util.List<java.util.Map<String, Object>>> capabilities() {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (AgentProductProfile item : profileService.list(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(AgentProductProfile::getApplicationId, applicationId()).eq(AgentProductProfile::getStatus, 1)
                .eq(AgentProductProfile::getDeleted, false))) {
            if (!accountService.isProductAllowed(serviceAccountId(), item.getId())) continue;
            java.util.Map<String, Object> value = new java.util.LinkedHashMap<String, Object>();
            value.put("code", item.getCode()); value.put("name", item.getName()); value.put("productType", item.getProductType()); value.put("version", item.getVersionNo());
            value.put("targetType", StringUtils.isNotBlank(item.getWorkflowId()) ? "WORKFLOW" : "AGENT");
            value.put("inputSchema", item.getInputSchema()); value.put("outputSchema", item.getOutputSchema()); result.add(value);
        }
        return WebResponse.OK(result);
    }

    @ApiOperation("启动业务工作流")
    @PostMapping("/workflows/runs")
    public WebResponse<OpenApiRunVo> startWorkflow(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
                                                     @RequestBody OpenApiWorkflowStartDto request) {
        if (request == null) throw new ServerException(422, "请求不能为空");
        String applicationId = applicationId();
        AgentProductProfile product = requiredProduct(request.getProductCode(), "WORKFLOW");
        if (request == null || product == null || StringUtils.isBlank(request.getBusinessId()))
            throw new ServerException(422, "productCode 和 businessId 不能为空");
        request.setIdempotencyKey(StringUtils.defaultIfBlank(idempotencyHeader, request.getIdempotencyKey()));
        if (StringUtils.isBlank(request.getIdempotencyKey())) throw new ServerException(422, "Idempotency-Key 不能为空");
        AgentWorkflow workflow = workflowService.getById(product.getWorkflowId());
        if (workflow == null || !Integer.valueOf(1).equals(workflow.getStatus())) throw new ServerException(404, "未找到已发布工作流");
        accountService.assertWorkflowStartAllowed(serviceAccountId(), workflow.getId());
        AgentWorkflowBusinessStartDto dto = new AgentWorkflowBusinessStartDto();
        dto.setBusinessId(request.getBusinessId()); dto.setBusinessType(request.getBusinessType());
        dto.setIdempotencyKey(request.getIdempotencyKey()); dto.setCallbackUrl(request.getCallbackUrl());
        dto.setDeadlineAt(request.getDeadlineAt()); dto.setVariables(request.getInput() == null ? Collections.<String, Object>emptyMap() : request.getInput());
        AgentWorkflowInstance instance = executionService.startBusiness(workflow.getId(), dto, CurrentUser.getUser().get("userId"));
        return WebResponse.OK(run(instance.getId(), instance.getBusinessId(), instance.getStatus()));
    }

    @ApiOperation("查询业务工作流运行状态")
    @GetMapping("/workflows/runs/{runId}")
    public WebResponse<OpenApiRunVo> workflowRun(@PathVariable String runId) {
        AgentWorkflowInstanceVo instance = executionService.detail(runId, CurrentUser.getUser().get("userId"));
        return WebResponse.OK(run(instance.getId(), instance.getBusinessId(), instance.getStatus()));
    }

    @ApiOperation("取消业务工作流运行")
    @PostMapping("/workflows/runs/{runId}/cancel")
    public WebResponse<OpenApiRunVo> cancelWorkflow(@PathVariable String runId) {
        executionService.terminate(runId, CurrentUser.getUser().get("userId"));
        AgentWorkflowInstanceVo instance = executionService.detail(runId, CurrentUser.getUser().get("userId"));
        return WebResponse.OK(run(instance.getId(), instance.getBusinessId(), instance.getStatus()));
    }

    @ApiOperation("调用标准 Agent 对话")
    @PostMapping("/agents/chat")
    public WebResponse<OpenApiAgentChatVo> chat(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
                                                 @RequestBody OpenApiAgentChatDto request) {
        if (request == null || StringUtils.isBlank(request.getProductCode()) || StringUtils.isBlank(request.getInput()))
            throw new ServerException(422, "productCode 和 input 不能为空");
        AgentProductProfile product = requiredProduct(request.getProductCode(), "AGENT");
        AgentDefinition agent = agentService.getById(product.getAgentDefinitionId());
        if (agent == null) throw new ServerException(404, "未找到 Agent");
        accountService.assertAgentCallAllowed(serviceAccountId(), agent.getId());
        if ("DEEP".equalsIgnoreCase(agent.getExecutionMode())) throw new ServerException(422, "Deep Agent 请使用异步任务接口");
        request.setIdempotencyKey(StringUtils.defaultIfBlank(idempotencyHeader, request.getIdempotencyKey()));
        if (StringUtils.isBlank(request.getIdempotencyKey())) throw new ServerException(422, "Idempotency-Key 不能为空");
        OpenApiAgentChatVo response = idempotencyService.execute(applicationId() + ":agent:" + agent.getId(), request.getIdempotencyKey(), OpenApiAgentChatVo.class, () -> {
            AgentChatDto dto = new AgentChatDto();
            dto.setAgentId(agent.getId()); dto.setConversationId(request.getConversationId()); dto.setMessage(request.getInput());
            dto.setRequestId(request.getIdempotencyKey());
            return safeChat(chatService.chat(dto));
        });
        return WebResponse.OK(response);
    }

    @ApiOperation("启动异步 Agent 运行")
    @PostMapping("/agents/runs")
    public WebResponse<OpenApiAgentRunVo> startAgentRun(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
                                                         @RequestBody OpenApiAgentRunStartDto request) {
        if (request == null || StringUtils.isBlank(request.getProductCode()) || StringUtils.isBlank(request.getInput()))
            throw new ServerException(422, "productCode 和 input 不能为空");
        request.setIdempotencyKey(StringUtils.defaultIfBlank(idempotencyHeader, request.getIdempotencyKey()));
        if (StringUtils.isBlank(request.getIdempotencyKey())) throw new ServerException(422, "Idempotency-Key 不能为空");
        String applicationId = applicationId();
        AgentProductProfile product = requiredProduct(request.getProductCode(), "AGENT");
        AgentDefinition agent = agentService.getById(product.getAgentDefinitionId());
        if (agent == null) throw new ServerException(404, "未找到 Agent");
        accountService.assertAgentCallAllowed(serviceAccountId(), agent.getId());
        String userId = CurrentUser.getUser().get("userId");
        String marker = "openapi:" + request.getIdempotencyKey();
        AgentRun existing = agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getApplicationId, applicationId).eq(AgentRun::getAgentDefinitionId, agent.getId())
                .eq(AgentRun::getExternalRunId, marker).eq(AgentRun::getDeleted, false).last("LIMIT 1"));
        if (existing != null) return WebResponse.OK(agentRun(existing, request.getBusinessId()));
        AgentConversation conversation = resolveConversation(agent, request.getConversationId());
        if ("DEEP".equalsIgnoreCase(agent.getExecutionMode())) {
            String runId = deepAgentRunService.startBusinessRun(agent, userId, conversation.getId(),
                    request.getInput(), marker, null);
            AgentRun run = new AgentRun(); run.setId(runId); run.setBusinessId(request.getBusinessId()); agentRunService.updateById(run);
            return WebResponse.OK(agentRun(agentRunService.getById(runId), request.getBusinessId()));
        }
        AgentRun run = new AgentRun();
        run.setApplicationId(applicationId); run.setAgentDefinitionId(agent.getId()); run.setUserId(userId);
        run.setBusinessId(request.getBusinessId()); run.setConversationId(conversation.getId()); run.setInputContent(JSON.toJSONString(request)); run.setStatus(3);
        run.setExecutionMode(StringUtils.defaultIfBlank(agent.getExecutionMode(), "STANDARD")); run.setModel(agent.getModel()); run.setExternalRunId(marker);
        agentRunService.save(run);
        executor.execute(() -> executeStandardAgentRun(run.getId(), agent.getId(), conversation.getId(), userId, request.getInput()));
        return WebResponse.OK(agentRun(run, request.getBusinessId()));
    }

    @ApiOperation("查询 Agent 运行状态")
    @GetMapping("/agents/runs/{runId}")
    public WebResponse<OpenApiAgentRunVo> agentRun(@PathVariable String runId) {
        return WebResponse.OK(agentRun(requiredAgentRun(runId), null));
    }

    @ApiOperation("取消 Agent 运行")
    @PostMapping("/agents/runs/{runId}/cancel")
    public WebResponse<OpenApiAgentRunVo> cancelAgentRun(@PathVariable String runId) {
        AgentRun run = requiredAgentRun(runId);
        if ("DEEP".equalsIgnoreCase(run.getExecutionMode())) deepAgentRunService.markCancelled(runId);
        else agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getStatus, 5)
                .eq(AgentRun::getId, runId).in(AgentRun::getStatus, 3, 4));
        return WebResponse.OK(agentRun(requiredAgentRun(runId), null));
    }

    private void executeStandardAgentRun(String runId, String agentId, String conversationId, String userId, String input) {
        if (!agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getStatus, 4)
                .eq(AgentRun::getId, runId).eq(AgentRun::getStatus, 3))) return;
        try {
            AgentChatDto dto = new AgentChatDto(); dto.setAgentId(agentId); dto.setConversationId(conversationId); dto.setUserId(userId); dto.setMessage(input);
            AgentMessageVo message = chatService.chat(dto);
            agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getConversationId, message.getConversationId())
                    .set(AgentRun::getMessageId, message.getId()).set(AgentRun::getOutputContent, message.getContent()).set(AgentRun::getStatus, 0)
                    .eq(AgentRun::getId, runId).eq(AgentRun::getStatus, 4));
        } catch (RuntimeException ex) {
            agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getStatus, 1)
                    .set(AgentRun::getErrorMsg, "OpenAPI agent run failed").eq(AgentRun::getId, runId).eq(AgentRun::getStatus, 4));
        }
    }

    private AgentConversation resolveConversation(AgentDefinition agent, String conversationId) {
        if (StringUtils.isNotBlank(conversationId)) {
            AgentConversation conversation = conversationService.getById(conversationId);
            if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())
                    || !StringUtils.equals(conversation.getApplicationId(), applicationId())
                    || !StringUtils.equals(conversation.getAgentDefinitionId(), agent.getId())) throw new ServerException(403, "无权访问会话");
            return conversation;
        }
        AgentConversation conversation = new AgentConversation();
        conversation.setApplicationId(applicationId()); conversation.setAgentDefinitionId(agent.getId());
        conversation.setUserId(CurrentUser.getUser().get("userId")); conversation.setTitle("开放 API 调用");
        conversation.setMessageCount(0); conversation.setStatus(0); conversation.setToolApprovalPolicy("never");
        conversationService.save(conversation);
        return conversation;
    }

    private AgentRun requiredAgentRun(String runId) {
        AgentRun run = agentRunService.getById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) throw new ServerException(404, "未找到运行记录");
        if (!StringUtils.equals(run.getApplicationId(), applicationId())) throw new ServerException(403, "无权访问运行记录");
        return run;
    }

    private OpenApiAgentRunVo agentRun(AgentRun run, String businessId) {
        OpenApiAgentRunVo value = new OpenApiAgentRunVo(); value.setRunId(run.getId()); value.setConversationId(run.getConversationId());
        value.setBusinessId(StringUtils.defaultIfBlank(businessId, run.getBusinessId())); value.setStatus(agentRunStatus(run.getStatus())); value.setTraceId(MDC.get("traceId"));
        if (Integer.valueOf(0).equals(run.getStatus())) value.setAnswer(run.getOutputContent());
        if (Integer.valueOf(1).equals(run.getStatus())) value.setErrorCode("AGENT_RUN_FAILED");
        return value;
    }

    private String agentRunStatus(Integer status) {
        if (Integer.valueOf(0).equals(status)) return "SUCCEEDED";
        if (Integer.valueOf(1).equals(status) || Integer.valueOf(2).equals(status)) return "FAILED";
        if (Integer.valueOf(3).equals(status)) return "QUEUED";
        if (Integer.valueOf(4).equals(status)) return "RUNNING";
        if (Integer.valueOf(5).equals(status)) return "CANCELLED";
        return "UNKNOWN";
    }

    private OpenApiRunVo run(String runId, String businessId, String status) {
        OpenApiRunVo value = new OpenApiRunVo(); value.setRunId(runId); value.setBusinessId(businessId); value.setStatus(status);
        value.setTraceId(MDC.get("traceId")); return value;
    }

    private AgentProductProfile requiredProduct(String productCode, String targetType) {
        if (StringUtils.isBlank(productCode)) return null;
        AgentProductProfile product = profileService.getOne(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(AgentProductProfile::getApplicationId, applicationId()).eq(AgentProductProfile::getCode, productCode)
                .eq(AgentProductProfile::getStatus, 1).eq(AgentProductProfile::getDeleted, false));
        if (product == null || !accountService.isProductAllowed(serviceAccountId(), product.getId()))
            throw new ServerException(403, "无权调用产品");
        boolean workflow = StringUtils.isNotBlank(product.getWorkflowId());
        if (("WORKFLOW".equals(targetType) && !workflow) || ("AGENT".equals(targetType) && workflow))
            throw new ServerException(422, "产品目标类型不匹配");
        return product;
    }
    private OpenApiAgentChatVo safeChat(AgentMessageVo message) {
        OpenApiAgentChatVo value = new OpenApiAgentChatVo();
        value.setConversationId(message.getConversationId()); value.setAnswer(message.getContent()); value.setCitations(message.getCitations());
        value.setRunId(message.getRunId()); value.setInteractionStatus(message.getInteractionStatus()); value.setInteractionType(message.getInteractionType());
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
