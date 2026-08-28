package com.aether.openapi.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.vo.AgentMessageVo;
import com.alibaba.fastjson2.JSON;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.exception.OpenApiException;
import com.aether.local.CurrentUser;
import com.aether.openapi.dto.OpenApiAgentChatDto;
import com.aether.openapi.dto.OpenApiAgentRunStartDto;
import com.aether.openapi.dto.OpenApiAgentInteractionDto;
import com.aether.openapi.dto.OpenApiWorkflowStartDto;
import com.aether.openapi.vo.OpenApiRunVo;
import com.aether.openapi.vo.OpenApiAgentChatVo;
import com.aether.openapi.vo.OpenApiAgentRunVo;
import com.aether.openapi.service.OpenApiIdempotencyService;
import com.aether.openapi.service.TrustedContextService;
import com.aether.openapi.service.ProductSnapshotService;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

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
    private final AgentMessageService messageService;
    private final DeepAgentRunService deepAgentRunService;
    private final ThreadPoolTaskExecutor executor;
    private final AgentProductProfileService profileService;
    private final OpenApiIdempotencyService idempotencyService;
    private final TrustedContextService trustedContextService;
    private final ProductSnapshotService productSnapshotService;

    public OpenApiController(ServiceAccountService accountService, AgentWorkflowService workflowService,
                              AgentWorkflowExecutionService executionService, AgentDefinitionService agentService,
                              AgentChatService chatService, AgentRunService agentRunService,
                              AgentConversationService conversationService, AgentMessageService messageService, DeepAgentRunService deepAgentRunService,
                              @Qualifier("asyncPoolTaskExecutor") ThreadPoolTaskExecutor executor,
                              AgentProductProfileService profileService, OpenApiIdempotencyService idempotencyService,
                              TrustedContextService trustedContextService, ProductSnapshotService productSnapshotService) {
        this.accountService = accountService;
        this.workflowService = workflowService;
        this.executionService = executionService;
        this.agentService = agentService;
        this.chatService = chatService;
        this.agentRunService = agentRunService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.deepAgentRunService = deepAgentRunService;
        this.executor = executor;
        this.profileService = profileService;
        this.idempotencyService = idempotencyService;
        this.trustedContextService = trustedContextService;
        this.productSnapshotService = productSnapshotService;
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
            value.put("code", item.getCode()); value.put("productId", item.getProductId()); value.put("productProfileId", item.getId());
            value.put("name", item.getName()); value.put("productType", item.getProductType()); value.put("version", item.getVersionNo());
            value.put("targetType", StringUtils.isNotBlank(item.getWorkflowId()) ? "WORKFLOW" : "AGENT");
            value.put("apiProtocolVersion", item.getApiProtocolVersion());
            if ("WORKFLOW".equals(item.getProductType())) { value.put("inputSchema", item.getInputSchema()); value.put("outputSchema", item.getOutputSchema()); }
            else value.put("allowedContextKeys", item.getAllowedContextKeys());
            result.add(value);
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
            throw apiError(422, "REQUEST_INVALID");
        AgentProductProfile product = requiredProduct(request.getProductCode(), "AGENT");
        AgentDefinition agent = resolvedProductAgent(product);
        if (agent == null) throw apiError(404, "PRODUCT_NOT_FOUND");
        accountService.assertAgentProductCallAllowed(serviceAccountId(), product.getId());
        if ("DEEP".equalsIgnoreCase(agent.getExecutionMode())) throw apiError(422, "AGENT_ASYNC_REQUIRED");
        request.setIdempotencyKey(StringUtils.defaultIfBlank(idempotencyHeader, request.getIdempotencyKey()));
        if (StringUtils.isBlank(request.getIdempotencyKey())) throw apiError(422, "IDEMPOTENCY_KEY_REQUIRED");
        String fingerprint = fingerprint("chat", product.getId(), request.getConversationId(), request.getBusinessId(), request.getInput(), request.getContext(), request.getExpectedLastSequence());
        OpenApiAgentChatVo response = idempotencyService.execute(serviceAccountId() + ":" + product.getId() + ":chat", request.getIdempotencyKey(), fingerprint, OpenApiAgentChatVo.class, () -> {
            AgentConversation conversation = resolveOpenApiConversation(product, agent, request.getConversationId(), request.getContext(), request.getExpectedLastSequence());
            AgentChatDto dto = new AgentChatDto();
            dto.setAgentId(agent.getId()); dto.setConversationId(conversation.getId()); dto.setMessage(request.getInput());
            dto.setRequestId(request.getIdempotencyKey());
            dto.setOpenApi(true);
            dto.setAgentSnapshot(agent);
            return safeChat(chatService.chat(dto));
        });
        return WebResponse.OK(response);
    }

    @ApiOperation("启动异步 Agent 运行")
    @PostMapping("/agents/runs")
    public WebResponse<OpenApiAgentRunVo> startAgentRun(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
                                                         @RequestBody OpenApiAgentRunStartDto request) {
        if (request == null || StringUtils.isBlank(request.getProductCode()) || StringUtils.isBlank(request.getInput()))
            throw apiError(422, "REQUEST_INVALID");
        request.setIdempotencyKey(StringUtils.defaultIfBlank(idempotencyHeader, request.getIdempotencyKey()));
        if (StringUtils.isBlank(request.getIdempotencyKey())) throw apiError(422, "IDEMPOTENCY_KEY_REQUIRED");
        String applicationId = applicationId();
        AgentProductProfile product = requiredProduct(request.getProductCode(), "AGENT");
        AgentDefinition agent = resolvedProductAgent(product);
        if (agent == null) throw apiError(404, "PRODUCT_NOT_FOUND");
        accountService.assertAgentProductCallAllowed(serviceAccountId(), product.getId());
        String userId = CurrentUser.getUser().get("userId");
        // Existing run-level uniqueness is keyed by agent/user/externalRunId, so
        // include the product version as well as the caller-visible key.
        String marker = "openapi:" + product.getId() + ":" + request.getIdempotencyKey();
        String fingerprint = fingerprint("run", product.getId(), request.getConversationId(), request.getBusinessId(), request.getInput(), request.getContext(), request.getExpectedLastSequence());
        AgentRun existing = agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getApplicationId, applicationId).eq(AgentRun::getProductProfileId, product.getId())
                .eq(AgentRun::getServiceAccountId, serviceAccountId())
                .eq(AgentRun::getExternalRunId, marker).eq(AgentRun::getDeleted, false).last("LIMIT 1"));
        if (existing != null) {
            if (!StringUtils.equals(existing.getRequestFingerprint(), fingerprint)) throw apiError(409, "IDEMPOTENCY_KEY_REUSED");
            return WebResponse.OK(agentRun(existing, request.getBusinessId()));
        }
        AgentConversation conversation = resolveOpenApiConversation(product, agent, request.getConversationId(), request.getContext(), request.getExpectedLastSequence());
        if ("DEEP".equalsIgnoreCase(agent.getExecutionMode())) {
            String runId = deepAgentRunService.startBusinessRun(agent, userId, conversation.getId(),
                    request.getInput(), marker, null);
            AgentRun run = new AgentRun(); run.setId(runId); run.setBusinessId(request.getBusinessId());
            applyOpenApiRunBoundary(run, product, conversation, fingerprint); agentRunService.updateById(run);
            return WebResponse.OK(agentRun(agentRunService.getById(runId), request.getBusinessId()));
        }
        AgentRun run = new AgentRun();
        run.setApplicationId(applicationId); run.setAgentDefinitionId(agent.getId()); run.setUserId(userId);
        run.setBusinessId(request.getBusinessId()); run.setConversationId(conversation.getId()); run.setInputContent(redactedRunInput(request)); run.setStatus(3);
        run.setExecutionMode(StringUtils.defaultIfBlank(agent.getExecutionMode(), "STANDARD")); run.setModel(agent.getModel()); run.setExternalRunId(marker);
        applyOpenApiRunBoundary(run, product, conversation, fingerprint);
        agentRunService.save(run);
        executor.execute(() -> executeStandardAgentRun(run.getId(), agent, conversation.getId(), userId, request.getInput()));
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

    @ApiOperation("提交 Agent 交互补充输入")
    @PostMapping("/agents/runs/{runId}/interactions/{interactionId}/submit")
    public WebResponse<OpenApiAgentRunVo> submitInteraction(@PathVariable String runId, @PathVariable String interactionId,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String header,
                                                             @RequestBody OpenApiAgentInteractionDto request) {
        return WebResponse.OK(resumeInteraction(runId, interactionId, header, request, false));
    }

    @ApiOperation("确认或拒绝 Agent 交互")
    @PostMapping("/agents/runs/{runId}/interactions/{interactionId}/confirm")
    public WebResponse<OpenApiAgentRunVo> confirmInteraction(@PathVariable String runId, @PathVariable String interactionId,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String header,
                                                              @RequestBody OpenApiAgentInteractionDto request) {
        return WebResponse.OK(resumeInteraction(runId, interactionId, header, request, true));
    }

    @ApiOperation("人工处理结束后恢复 AI 会话")
    @PostMapping("/agents/conversations/{conversationId}/handoff/release")
    public WebResponse<Void> releaseHandoff(@PathVariable String conversationId) {
        AgentConversation conversation = conversationService.getById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted()) || !StringUtils.equals(conversation.getApplicationId(), applicationId())
                || StringUtils.isBlank(conversation.getProductProfileId()) || !accountService.isProductAllowed(serviceAccountId(), conversation.getProductProfileId()))
            throw apiError(422, "CONVERSATION_NOT_ACCESSIBLE");
        conversationService.update(null, Wrappers.lambdaUpdate(AgentConversation.class).set(AgentConversation::getHandoffStatus, "AI_HANDLING")
                .eq(AgentConversation::getId, conversationId));
        return WebResponse.OK((Void) null);
    }

    private void executeStandardAgentRun(String runId, AgentDefinition agent, String conversationId, String userId, String input) {
        if (!agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getStatus, 4)
                .eq(AgentRun::getId, runId).eq(AgentRun::getStatus, 3))) return;
        try {
            AgentChatDto dto = new AgentChatDto(); dto.setAgentId(agent.getId()); dto.setConversationId(conversationId); dto.setUserId(userId); dto.setMessage(input);
            dto.setOpenApi(true); dto.setAgentSnapshot(agent);
            AgentMessageVo message = chatService.chat(dto);
            agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getConversationId, message.getConversationId())
                    .set(AgentRun::getMessageId, message.getId()).set(AgentRun::getOutputContent, message.getContent()).set(AgentRun::getStatus, 0)
                    .eq(AgentRun::getId, runId).eq(AgentRun::getStatus, 4));
        } catch (RuntimeException ex) {
            agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class).set(AgentRun::getStatus, 1)
                    .set(AgentRun::getErrorMsg, "OpenAPI agent run failed").eq(AgentRun::getId, runId).eq(AgentRun::getStatus, 4));
        }
    }

    private OpenApiAgentRunVo resumeInteraction(String runId, String interactionId, String header,
                                                OpenApiAgentInteractionDto request, boolean confirmation) {
        if (request == null || request.getAnswer() == null) throw apiError(422, "INTERACTION_NOT_ACTIONABLE");
        String key = StringUtils.defaultIfBlank(header, request.getIdempotencyKey());
        if (StringUtils.isBlank(key)) throw apiError(422, "IDEMPOTENCY_KEY_REQUIRED");
        AgentRun run = requiredAgentRun(runId);
        AgentMessage interaction = messageService.getById(interactionId);
        if (interaction == null || Boolean.TRUE.equals(interaction.getDeleted()) || !StringUtils.equals(run.getConversationId(), interaction.getConversationId())
                || !"interaction".equalsIgnoreCase(interaction.getMessageType()) || !"pending".equalsIgnoreCase(interaction.getInteractionStatus()))
            throw apiError(422, "INTERACTION_NOT_ACTIONABLE");
        String type = JSON.parseObject(StringUtils.defaultString(interaction.getQuestionConfig())).getString("type");
        if (confirmation != "confirm".equalsIgnoreCase(type)) throw apiError(422, "INTERACTION_NOT_ACTIONABLE");
        String fingerprint = fingerprint("interaction", run.getProductProfileId(), run.getConversationId(), runId,
                JSON.toJSONString(request.getAnswer()), Collections.<String, Object>emptyMap(), null);
        return idempotencyService.execute(serviceAccountId() + ":" + run.getProductProfileId() + ":interaction", key, fingerprint,
                OpenApiAgentRunVo.class, () -> doResumeInteraction(run, interaction, request.getAnswer()));
    }

    private OpenApiAgentRunVo doResumeInteraction(AgentRun run, AgentMessage interaction, Map<String, Object> answer) {
        if ("DEEP".equalsIgnoreCase(run.getExecutionMode())) {
            deepAgentRunService.resumeToolApproval(run.getConversationId(), interaction.getId(), run.getUserId(), answer);
            return agentRun(requiredAgentRun(run.getId()), null);
        }
        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId(run.getAgentDefinitionId()); dto.setConversationId(run.getConversationId()); dto.setUserId(run.getUserId());
        dto.setParentMessageId(interaction.getId()); dto.setAnswer(answer); dto.setOpenApi(true);
        AgentProductProfile product = profileService.getById(run.getProductProfileId());
        if (product == null) throw apiError(404, "PRODUCT_NOT_FOUND");
        dto.setAgentSnapshot(resolvedProductAgent(product));
        AgentMessageVo response = chatService.chat(dto);
        OpenApiAgentRunVo value = new OpenApiAgentRunVo();
        value.setRunId(response.getRunId()); value.setConversationId(response.getConversationId()); value.setStatus("SUCCEEDED");
        value.setAnswer(response.getContent()); value.setTraceId(MDC.get("traceId"));
        return value;
    }

    private AgentConversation resolveOpenApiConversation(AgentProductProfile product, AgentDefinition agent, String conversationId,
                                                         Map<String, Object> context, Long expectedLastSequence) {
        if (StringUtils.isNotBlank(conversationId)) {
            AgentConversation conversation = conversationService.getById(conversationId);
            if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())
                    || !StringUtils.equals(conversation.getApplicationId(), applicationId())
                    || !StringUtils.equals(conversation.getAgentDefinitionId(), agent.getId())
                    || !StringUtils.equals(conversation.getProductProfileId(), product.getId())) throw apiError(422, "CONVERSATION_NOT_ACCESSIBLE");
            if ("HUMAN_HANDLING".equals(conversation.getHandoffStatus())) throw apiError(422, "CONVERSATION_NOT_ACCESSIBLE");
            if (!StringUtils.equals(conversation.getServiceAccountId(), serviceAccountId()) && !accountService.isProductAllowed(serviceAccountId(), product.getId()))
                throw apiError(403, "PRODUCT_NOT_ALLOWED");
            return advanceConversation(conversation, product, context, expectedLastSequence);
        }
        AgentConversation conversation = new AgentConversation();
        conversation.setApplicationId(applicationId()); conversation.setAgentDefinitionId(agent.getId());
        conversation.setUserId(CurrentUser.getUser().get("userId")); conversation.setTitle("开放 API 调用");
        conversation.setMessageCount(0); conversation.setStatus(0); conversation.setToolApprovalPolicy("never");
        conversation.setProductProfileId(product.getId()); conversation.setProductVersionNo(product.getVersionNo());
        conversation.setProductSnapshotId(product.getPublishedSnapshotId()); conversation.setServiceAccountId(serviceAccountId());
        conversation.setTrustedContext(trustedContextService.merge(product, null, context)); conversation.setContextVersion(context == null || context.isEmpty() ? 0 : 1);
        conversation.setMessageSequence(1L); conversation.setRuntimeVersion(1);
        conversationService.save(conversation);
        return conversation;
    }

    private AgentRun requiredAgentRun(String runId) {
        AgentRun run = agentRunService.getById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) throw apiError(404, "PRODUCT_NOT_FOUND");
        if (!StringUtils.equals(run.getApplicationId(), applicationId()) || StringUtils.isBlank(run.getProductProfileId())
                || !accountService.isProductAllowed(serviceAccountId(), run.getProductProfileId())) throw apiError(403, "PRODUCT_NOT_ALLOWED");
        return run;
    }

    private AgentConversation advanceConversation(AgentConversation conversation, AgentProductProfile product,
                                                  Map<String, Object> context, Long expectedLastSequence) {
        long currentSequence = conversation.getMessageSequence() == null ? 0L : conversation.getMessageSequence();
        if (expectedLastSequence != null && expectedLastSequence.longValue() != currentSequence)
            throw apiError(409, "CONVERSATION_SEQUENCE_CONFLICT");
        String trusted = trustedContextService.merge(product, conversation.getTrustedContext(), context);
        int version = conversation.getRuntimeVersion() == null ? 0 : conversation.getRuntimeVersion();
        boolean updated = conversationService.update(null, Wrappers.lambdaUpdate(AgentConversation.class)
                .set(AgentConversation::getMessageSequence, currentSequence + 1)
                .set(AgentConversation::getRuntimeVersion, version + 1)
                .set(AgentConversation::getTrustedContext, trusted)
                .set(AgentConversation::getContextVersion, StringUtils.equals(trusted, conversation.getTrustedContext())
                        ? conversation.getContextVersion() : (conversation.getContextVersion() == null ? 1 : conversation.getContextVersion() + 1))
                .eq(AgentConversation::getId, conversation.getId()).eq(AgentConversation::getRuntimeVersion, version));
        if (!updated) throw apiError(409, "CONVERSATION_SEQUENCE_CONFLICT");
        conversation.setMessageSequence(currentSequence + 1); conversation.setRuntimeVersion(version + 1); conversation.setTrustedContext(trusted);
        return conversation;
    }

    private void applyOpenApiRunBoundary(AgentRun run, AgentProductProfile product, AgentConversation conversation, String fingerprint) {
        run.setProductProfileId(product.getId()); run.setProductSnapshotId(product.getPublishedSnapshotId());
        run.setServiceAccountId(serviceAccountId()); run.setTrustedContext(conversation.getTrustedContext());
        run.setContextVersion(conversation.getContextVersion()); run.setRequestFingerprint(fingerprint);
    }

    private String fingerprint(String operation, String productId, String conversationId, String businessId, String input,
                               Map<String, Object> context, Long expectedLastSequence) {
        Map<String, Object> value = new TreeMap<String, Object>();
        value.put("operation", operation); value.put("productProfileId", productId); value.put("conversationId", conversationId);
        value.put("businessId", businessId); value.put("input", input); value.put("context", context == null ? Collections.emptyMap() : new TreeMap<String, Object>(context));
        value.put("expectedLastSequence", expectedLastSequence);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String redactedRunInput(OpenApiAgentRunStartDto request) {
        Map<String, Object> audit = new LinkedHashMap<String, Object>();
        audit.put("productCode", request.getProductCode()); audit.put("conversationId", request.getConversationId());
        audit.put("businessId", request.getBusinessId()); audit.put("input", request.getInput());
        audit.put("context", request.getContext() == null || request.getContext().isEmpty() ? Collections.emptyMap() : "***");
        return JSON.toJSONString(audit);
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
            throw apiError(403, "PRODUCT_NOT_ALLOWED");
        boolean workflow = StringUtils.isNotBlank(product.getWorkflowId());
        if (("WORKFLOW".equals(targetType) && !workflow) || ("AGENT".equals(targetType) && workflow))
            throw apiError(422, "PRODUCT_TYPE_MISMATCH");
        return product;
    }

    private AgentDefinition resolvedProductAgent(AgentProductProfile product) {
        AgentDefinition frozen = productSnapshotService.resolveAgent(product);
        AgentDefinition agent = frozen == null ? agentService.getById(product.getAgentDefinitionId()) : frozen;
        if (agent == null || !StringUtils.equals(agent.getId(), product.getAgentDefinitionId())) throw apiError(404, "PRODUCT_NOT_FOUND");
        return agent;
    }
    private OpenApiAgentChatVo safeChat(AgentMessageVo message) {
        OpenApiAgentChatVo value = new OpenApiAgentChatVo();
        value.setConversationId(message.getConversationId()); value.setAnswer(message.getContent()); value.setCitations(citations(message.getCitations()));
        value.setRunId(message.getRunId()); value.setInteractionStatus(message.getInteractionStatus()); value.setInteractionType(message.getInteractionType());
        if (StringUtils.isNotBlank(message.getQuestionConfig())) {
            try { value.setInteractionData(JSON.parseObject(message.getQuestionConfig(), Map.class)); } catch (RuntimeException ignored) { /* legacy malformed data is not exposed */ }
        }
        value.setTraceId(MDC.get("traceId")); return value;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> citations(String raw) {
        if (StringUtils.isBlank(raw)) return Collections.emptyList();
        try {
            java.util.List<Map<String, Object>> value = JSON.parseObject(raw, java.util.List.class);
            return value == null ? Collections.<Map<String, Object>>emptyList() : value;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }
    private String serviceAccountId() {
        Map<String, String> user = CurrentUser.getUser();
        if (user == null || StringUtils.isBlank(user.get("serviceAccountId"))) throw apiError(401, "SERVICE_ACCOUNT_INVALID");
        return user.get("serviceAccountId");
    }

    private OpenApiException apiError(int status, String errorCode) { return new OpenApiException(status, errorCode); }
    private String applicationId() {
        Map<String, String> user = CurrentUser.getUser();
        return user == null ? "0" : StringUtils.defaultIfBlank(user.get("applicationId"), "0");
    }
}
