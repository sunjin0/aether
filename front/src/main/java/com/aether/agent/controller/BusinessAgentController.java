package com.aether.agent.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.dto.BusinessAgentRunCreateDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentRunStepService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.agent.vo.BusinessAgentOptionVo;
import com.aether.agent.vo.BusinessAgentRunVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外部系统通过服务账号调用 Agent 的 front 接入 API。
 */
@Api(tags = "业务 Agent 接入 API")
@RestController
@RequestMapping("/api/business/agents")
public class BusinessAgentController {
    private static final int RUN_STATUS_SUCCESS = 0;
    private static final int RUN_STATUS_FAILED = 1;
    private static final int RUN_STATUS_QUEUED = 3;
    private static final int RUN_STATUS_RUNNING = 4;

    private final ServiceAccountService serviceAccountService;
    private final AgentChatService agentChatService;
    private final AgentRunService agentRunService;
    private final AgentRunStepService agentRunStepService;
    private final AgentConversationService conversationService;
    private final DeepAgentRunService deepAgentRunService;
    private final ThreadPoolTaskExecutor executor;

    public BusinessAgentController(ServiceAccountService serviceAccountService,
                                   AgentChatService agentChatService,
                                   AgentRunService agentRunService,
                                   AgentRunStepService agentRunStepService,
                                   AgentConversationService conversationService,
                                   DeepAgentRunService deepAgentRunService,
                                   @Qualifier("asyncPoolTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.serviceAccountService = serviceAccountService;
        this.agentChatService = agentChatService;
        this.agentRunService = agentRunService;
        this.agentRunStepService = agentRunStepService;
        this.conversationService = conversationService;
        this.deepAgentRunService = deepAgentRunService;
        this.executor = executor;
    }

    /**
     * 查询当前服务账号可调用 Agent。
     */
    @ApiOperation("查询当前服务账号可调用 Agent")
    @GetMapping
    public WebResponse<List<BusinessAgentOptionVo>> agents() {
        ServiceAccount account = currentAccount();
        List<String> ids = parseIds(account.getAllowedAgentIds());
        if (ids.isEmpty()) return WebResponse.OK(Collections.<BusinessAgentOptionVo>emptyList());
        List<AgentDefinition> agents = ids.isEmpty() ? Collections.<AgentDefinition>emptyList()
                : ids.stream().map(id -> {
                    try {
                        return agentChatService.getEnabledAgent(id);
                    } catch (RuntimeException ignored) {
                        return null;
                    }
                }).filter(item -> item != null).collect(Collectors.toList());
        return WebResponse.OK(agents.stream().map(this::toOption).collect(Collectors.toList()));
    }

    /**
     * 异步提交 Agent 运行。
     */
    @ApiOperation("外部系统异步提交 Agent 运行")
    @PostMapping("/{agentId}/runs")
    public WebResponse<BusinessAgentRunVo> run(@PathVariable String agentId,
                                               @RequestBody BusinessAgentRunCreateDto dto) {
        String serviceAccountId = currentServiceAccountId();
        String principalId = currentPrincipalId();
        if (dto == null || StringUtils.isBlank(dto.getMessage()))
            throw new ServerException(422, "message is required");
        AgentRun existing = existingIdempotentRun(agentId, principalId, dto.getIdempotencyKey());
        if (existing != null) return WebResponse.OK(toVo(existing));
        serviceAccountService.assertAgentCallAllowed(serviceAccountId, agentId);
        AgentDefinition agent = agentChatService.getEnabledAgent(agentId);
        if ("DEEP".equals(agent.getExecutionMode())) {
            AgentConversation conversation = resolveConversation(agent, principalId, dto.getConversationId());
            try {
                String runId = deepAgentRunService.startBusinessRun(agent, principalId, conversation.getId(), dto.getMessage(),
                        StringUtils.isBlank(dto.getIdempotencyKey()) ? null : idempotencyMarker(dto.getIdempotencyKey()), null);
                return WebResponse.OK(toVo(agentRunService.getById(runId)));
            } catch (DuplicateKeyException ex) {
                AgentRun duplicate = existingIdempotentRun(agentId, principalId, dto.getIdempotencyKey());
                if (duplicate != null) return WebResponse.OK(toVo(duplicate));
                throw ex;
            }
        }
        AgentRun run;
        try {
            run = createQueuedRun(agent, principalId, dto);
        } catch (DuplicateKeyException ex) {
            AgentRun duplicate = existingIdempotentRun(agentId, principalId, dto.getIdempotencyKey());
            if (duplicate != null) return WebResponse.OK(toVo(duplicate));
            throw ex;
        }
        executor.execute(() -> executeStandardRun(run.getId(), agentId, principalId, dto));
        return WebResponse.OK(toVo(run));
    }

    /**
     * 流式调用 Agent。
     */
    @ApiOperation("外部系统流式调用 Agent")
    @PostMapping(value = "/{agentId}/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String agentId, @RequestBody BusinessAgentRunCreateDto dto) {
        String serviceAccountId = currentServiceAccountId();
        String principalId = currentPrincipalId();
        if (dto == null || StringUtils.isBlank(dto.getMessage()))
            throw new ServerException(422, "message is required");
        serviceAccountService.assertAgentCallAllowed(serviceAccountId, agentId);
        AgentDefinition agent = agentChatService.getEnabledAgent(agentId);
        if ("DEEP".equals(agent.getExecutionMode())) {
            SseEmitter emitter = new SseEmitter(0L);
            ExternalStreamCallback callback = new ExternalStreamCallback(emitter);
            emitter.onCompletion(() -> callback.close());
            emitter.onTimeout(() -> callback.close());
            emitter.onError(error -> callback.close());
            executor.execute(() -> {
                try {
                    AgentConversation conversation = resolveConversation(agent, principalId, dto.getConversationId());
                    String runId = deepAgentRunService.startBusinessRun(agent, principalId, conversation.getId(), dto.getMessage(),
                            null, null);
                    JSONObject accepted = new JSONObject();
                    accepted.put("runId", runId);
                    accepted.put("conversationId", conversation.getId());
                    emitter.send(SseEmitter.event().name("accepted").data(accepted.toJSONString()));
                    streamDeepRunFromStore(runId, callback);
                } catch (Exception ex) {
                    completeWithError(emitter, ex);
                }
            });
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> {
            try {
                AgentChatDto chat = new AgentChatDto();
                chat.setAgentId(agentId);
                chat.setConversationId(dto.getConversationId());
                chat.setMessage(dto.getMessage());
                chat.setUserId(principalId);
                agentChatService.stream(chat, new ExternalStreamCallback(emitter));
            } catch (Exception ex) {
                completeWithError(emitter, ex);
            }
        });
        return emitter;
    }

    /**
     * 查询外部 Agent 运行状态。
     */
    @ApiOperation("查询外部 Agent 运行状态")
    @GetMapping("/runs/{runId}")
    public WebResponse<BusinessAgentRunVo> detail(@PathVariable String runId) {
        AgentRun run = agentRunService.getById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()))
            throw new ServerException(404, "run not found");
        if (!StringUtils.equals(run.getUserId(), currentPrincipalId()))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return WebResponse.OK(toVo(run));
    }

    private void executeStandardRun(String businessRunId, String agentId, String principalId,
                                    BusinessAgentRunCreateDto request) {
        AgentRun running = new AgentRun();
        running.setId(businessRunId);
        running.setStatus(RUN_STATUS_RUNNING);
        agentRunService.updateById(running);
        try {
            AgentChatDto dto = new AgentChatDto();
            dto.setAgentId(agentId);
            dto.setConversationId(request.getConversationId());
            dto.setMessage(request.getMessage());
            dto.setUserId(principalId);
            dto.setTemporary(false);
            AgentMessageVo message = agentChatService.chat(dto);
            AgentRun update = new AgentRun();
            update.setId(businessRunId);
            update.setConversationId(message.getConversationId());
            update.setMessageId(message.getId());
            update.setOutputContent(message.getContent());
            update.setStatus(resolveStatus(message));
            update.setErrorMsg(null);
            agentRunService.updateById(update);
        } catch (RuntimeException ex) {
            AgentRun update = new AgentRun();
            update.setId(businessRunId);
            update.setStatus(RUN_STATUS_FAILED);
            update.setErrorMsg(StringUtils.abbreviate(ex.getMessage(), 2048));
            agentRunService.updateById(update);
        }
    }

    private AgentRun createQueuedRun(AgentDefinition agent, String principalId, BusinessAgentRunCreateDto dto) {
        AgentRun run = new AgentRun();
        run.setAgentDefinitionId(agent.getId());
        run.setUserId(principalId);
        run.setConversationId(dto.getConversationId());
        run.setInputContent(JSON.toJSONString(inputSnapshot(dto)));
        run.setStatus(RUN_STATUS_QUEUED);
        run.setExecutionMode(StringUtils.defaultIfBlank(agent.getExecutionMode(), "STANDARD"));
        run.setModel(agent.getModel());
        run.setExternalRunId(StringUtils.isBlank(dto.getIdempotencyKey()) ? null : idempotencyMarker(dto.getIdempotencyKey()));
        agentRunService.save(run);
        return run;
    }

    private AgentConversation resolveConversation(AgentDefinition agent, String principalId, String conversationId) {
        if (StringUtils.isNotBlank(conversationId)) {
            AgentConversation conversation = conversationService.getById(conversationId);
            if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())
                    || !StringUtils.equals(conversation.getUserId(), principalId)
                    || !StringUtils.equals(conversation.getAgentDefinitionId(), agent.getId()))
                throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
            return conversation;
        }
        AgentConversation conversation = new AgentConversation();
        conversation.setUserId(principalId);
        conversation.setAgentDefinitionId(agent.getId());
        conversation.setTitle("外部业务调用");
        conversation.setMessageCount(0);
        conversation.setStatus(0);
        conversation.setToolApprovalPolicy("never");
        conversationService.save(conversation);
        return conversation;
    }

    private AgentRun existingIdempotentRun(String agentId, String principalId, String idempotencyKey) {
        if (StringUtils.isBlank(idempotencyKey)) return null;
        return agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getAgentDefinitionId, agentId)
                .eq(AgentRun::getUserId, principalId)
                .eq(AgentRun::getExternalRunId, idempotencyMarker(idempotencyKey))
                .eq(AgentRun::getDeleted, false)
                .last("LIMIT 1"));
    }

    private Map<String, Object> inputSnapshot(BusinessAgentRunCreateDto dto) {
        Map<String, Object> snapshot = new HashMap<String, Object>();
        snapshot.put("message", dto.getMessage());
        snapshot.put("variables", dto.getVariables());
        snapshot.put("metadata", dto.getMetadata());
        snapshot.put("idempotencyKey", dto.getIdempotencyKey());
        return snapshot;
    }

    private BusinessAgentRunVo toVo(AgentRun run) {
        BusinessAgentRunVo vo = new BusinessAgentRunVo();
        if (run == null) return vo;
        vo.setRunId(run.getId());
        vo.setAgentId(run.getAgentDefinitionId());
        vo.setConversationId(run.getConversationId());
        vo.setStatus(statusName(run.getStatus()));
        vo.setOutput(run.getOutputContent());
        vo.setErrorMessage(run.getErrorMsg());
        vo.setCreatedAt(run.getCreatedAt());
        vo.setUpdatedAt(run.getUpdatedAt());
        return vo;
    }

    private BusinessAgentOptionVo toOption(AgentDefinition agent) {
        BusinessAgentOptionVo vo = new BusinessAgentOptionVo();
        vo.setId(agent.getId());
        vo.setName(agent.getName());
        vo.setCode(agent.getCode());
        vo.setDescription(agent.getDescription());
        vo.setExecutionMode(agent.getExecutionMode());
        return vo;
    }

    private int resolveStatus(AgentMessageVo message) {
        if (message != null && "interaction".equals(message.getMessageType())) return RUN_STATUS_QUEUED;
        return RUN_STATUS_SUCCESS;
    }

    private String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        if (Integer.valueOf(RUN_STATUS_SUCCESS).equals(status)) return "SUCCEEDED";
        if (Integer.valueOf(RUN_STATUS_FAILED).equals(status)) return "FAILED";
        if (Integer.valueOf(RUN_STATUS_QUEUED).equals(status)) return "QUEUED";
        if (Integer.valueOf(RUN_STATUS_RUNNING).equals(status)) return "RUNNING";
        return String.valueOf(status);
    }

    private String idempotencyMarker(String value) {
        return "business:" + value;
    }

    private String currentServiceAccountId() {
        Map<String, String> user = CurrentUser.getUser();
        String serviceAccountId = user == null ? null : user.get("serviceAccountId");
        if (StringUtils.isBlank(serviceAccountId))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return serviceAccountId;
    }

    private ServiceAccount currentAccount() {
        ServiceAccount account = serviceAccountService.getById(currentServiceAccountId());
        if (account == null || Boolean.TRUE.equals(account.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("service-account.not-found"));
        return account;
    }

    private List<String> parseIds(String json) {
        if (StringUtils.isBlank(json)) return Collections.emptyList();
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception ex) {
            return new ArrayList<String>();
        }
    }

    private String currentPrincipalId() {
        Map<String, String> user = CurrentUser.getUser();
        String principalId = user == null ? null : user.get("principalId");
        if (StringUtils.isBlank(principalId)) principalId = user == null ? null : user.get("userId");
        if (StringUtils.isBlank(principalId))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return principalId;
    }

    private void completeWithError(SseEmitter emitter, Exception ex) {
        try {
            JSONObject error = new JSONObject();
            error.put("message", ex.getMessage());
            emitter.send(SseEmitter.event().name("error").data(error.toJSONString()));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    private void streamDeepRunFromStore(String runId, ExternalStreamCallback callback) throws InterruptedException {
        Set<String> emitted = new HashSet<String>();
        while (!callback.isClosed()) {
            for (AgentRunStep step : agentRunStepService.listByRunId(runId)) {
                String eventId = StringUtils.defaultIfBlank(step.getEventId(), step.getId());
                if (StringUtils.isBlank(eventId) || emitted.contains(eventId)) continue;
                emitted.add(eventId);
                callback.onRunStep(runId, stepJson(step));
            }
            AgentRun run = agentRunService.getById(runId);
            if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
                callback.onError(404, "run not found");
                return;
            }
            if (Integer.valueOf(RUN_STATUS_SUCCESS).equals(run.getStatus())) {
                callback.onDeepDone(toVo(run));
                return;
            }
            if (Integer.valueOf(RUN_STATUS_FAILED).equals(run.getStatus())) {
                callback.onError(500, StringUtils.defaultIfBlank(run.getErrorMsg(), "run failed"));
                return;
            }
            if (Integer.valueOf(5).equals(run.getStatus())) {
                callback.onError(0, "运行已取消");
                return;
            }
            Thread.sleep(1000L);
        }
    }

    private String stepJson(AgentRunStep step) {
        JSONObject data = new JSONObject();
        data.put("runId", step.getRunId());
        data.put("eventId", step.getEventId());
        data.put("eventType", step.getEventType());
        data.put("occurredAt", step.getOccurredAt());
        data.put("data", step.getData());
        return data.toJSONString();
    }

    private static class ExternalStreamCallback implements AgentStreamCallback {
        private final SseEmitter emitter;
        private volatile boolean closed;

        ExternalStreamCallback(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onMessage(String conversationId, String chunk) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("content", chunk);
            send("message", data);
        }

        @Override
        public void onReasoning(String conversationId, String chunk) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("content", chunk);
            send("reasoning", data);
        }

        @Override
        public void onToolCall(String conversationId, String toolCallJson) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("toolCall", toolCallJson);
            send("tool_call", data);
        }

        @Override
        public void onQuestion(String conversationId, String runId, AgentMessageVo question) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("runId", runId);
            data.put("question", question);
            send("question", data);
        }

        @Override
        public void onDone(String conversationId, String messageId, ModelStreamResponse response) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("messageId", messageId);
            data.put("content", response == null ? null : response.getContent());
            send("done", data);
            closed = true;
            emitter.complete();
        }

        @Override
        public void onError(int code, String message) {
            JSONObject data = new JSONObject();
            data.put("code", code);
            data.put("message", message);
            send("error", data);
            closed = true;
            emitter.complete();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void onStatus(String stage, String message) {
            JSONObject data = new JSONObject();
            data.put("stage", stage);
            data.put("message", message);
            send("status", data);
        }

        @Override
        public void onRunStep(String runId, String stepJson) {
            JSONObject data = new JSONObject();
            data.put("runId", runId);
            data.put("step", JSON.parseObject(stepJson));
            send("run_step", data);
        }

        void onDeepDone(BusinessAgentRunVo run) {
            send("done", run);
            closed = true;
            emitter.complete();
        }

        void close() {
            closed = true;
        }

        private void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(JSON.toJSONString(data)));
            } catch (IOException | IllegalStateException ignored) {
                closed = true;
            }
        }
    }
}
