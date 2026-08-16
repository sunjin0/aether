package com.aether.agent.controller;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.service.ModelProviderService;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/agent/deep-runs")
public class DeepAgentCallbackController {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentCallbackController.class);
    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DeepAgentRunService deepAgentRunService;
    private final AgentMessageService agentMessageService;
    private final DeepAgentConfig config;
    private final AgentRunPlanService planService;
    private final AgentDefinitionService agentDefinitionService;
    private final ModelProviderService modelProviderService;
    private final ModelCatalogService modelCatalogService;
    private final Map<String, AgentStreamCallback> activeCallbacks = new ConcurrentHashMap<>();

    public DeepAgentCallbackController(DeepAgentRunService deepAgentRunService, AgentMessageService agentMessageService,
                                       DeepAgentConfig config, AgentRunPlanService planService,
                                       AgentDefinitionService agentDefinitionService, ModelProviderService modelProviderService,
                                       ModelCatalogService modelCatalogService) {
        this.deepAgentRunService = deepAgentRunService;
        this.agentMessageService = agentMessageService;
        this.config = config;
        this.planService = planService;
        this.agentDefinitionService = agentDefinitionService;
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
    }

    public void registerCallback(String runId, AgentStreamCallback callback) {
        activeCallbacks.put(runId, callback);
    }

    public void removeCallback(String runId) {
        activeCallbacks.remove(runId);
    }

    public void reconcileTerminalCallback(String runId) {
        AgentStreamCallback callback = activeCallbacks.get(runId);
        if (callback == null || callback.isClosed()) return;
        AgentRun run = deepAgentRunService.getDeepRunForReconciliation(runId);
        if (Integer.valueOf(0).equals(run.getStatus()) && run.getMessageId() != null) {
            AgentMessage message = agentMessageService.getById(run.getMessageId());
            if (message != null && !Boolean.TRUE.equals(message.getDeleted())) {
                ModelStreamResponse response = new ModelStreamResponse();
                response.setContent(message.getContent());
                response.setReasoningContent(message.getReasoningContent());
                response.setToolCalls(message.getToolCalls());
                response.setModel(message.getModel());
                response.setPromptTokens(message.getPromptTokens());
                response.setCompletionTokens(message.getCompletionTokens());
                response.setTotalTokens(message.getTotalTokens());
                response.setReasoningTokens(message.getReasoningTokens());
                if (message.getCitations() != null) response.setSources(sourceList(JSON.parseObject("{\"sources\":" + message.getCitations() + "}"), "sources"));
                try {
                    callback.onDone(run.getConversationId(), message.getId(), response);
                } finally {
                    removeCallback(runId);
                }
            }
        } else if (Integer.valueOf(1).equals(run.getStatus()) || Integer.valueOf(5).equals(run.getStatus())) {
            notifyErrorAndRemove(runId, callback, Integer.valueOf(5).equals(run.getStatus()) ? 0 : 500,
                    Integer.valueOf(5).equals(run.getStatus()) ? "运行已取消" : run.getErrorMsg());
        }
    }

    @PostMapping("/callback/{runId}")
    public ResponseEntity<Void> callback(@PathVariable String runId, HttpServletRequest request) {
        try {
            byte[] bodyBytes = readBodyBytes(request.getInputStream());
            String body = new String(bodyBytes, StandardCharsets.UTF_8);

            if (!verifySignature(request, bodyBytes)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            JSONObject event = JSON.parseObject(body);

            String eventRunId = event.getString("run_id");
            if (!runId.equals(eventRunId)) {
                log.warn("回调 run_id 不匹配: path={} body={}", runId, eventRunId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String eventId = event.getString("event_id");
            String eventType = event.getString("event_type");
            long occurredAt = event.getLongValue("occurred_at", 0L);
            JSONObject eventData = event.getJSONObject("data");
            String dataJson = eventData != null ? eventData.toJSONString() : "{}";

            // 文本增量只用于当前聊天 SSE；不属于可审计的执行步骤，也不应写入执行记录。
            if ("message.delta".equals(eventType)) {
                AgentStreamCallback messageCallback = activeCallbacks.get(runId);
                if (messageCallback != null && !messageCallback.isClosed()) {
                    messageCallback.onMessage(deepAgentRunService.getDeepRunForReconciliation(runId).getConversationId(),
                            JSON.parseObject(dataJson).getString("chunk"));
                }
                return ResponseEntity.accepted().build();
            }

            boolean isNew;
            try {
                isNew = deepAgentRunService.handleCallback(runId, eventId, eventType, occurredAt, dataJson);
            } catch (IllegalArgumentException e) {
                log.warn("拒绝未知或非 Deep 回调: runId={}", runId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (isNew) {
                AgentStreamCallback callback = activeCallbacks.get(runId);
                if (callback != null && !callback.isClosed()) {
                    JSONObject stepEvent = new JSONObject();
                    stepEvent.put("runId", runId);
                    stepEvent.put("eventId", eventId);
                    stepEvent.put("eventType", eventType);
                    stepEvent.put("occurredAt", occurredAt);
                    stepEvent.put("data", eventData != null ? eventData : new JSONObject());
                    callback.onRunStep(runId, stepEvent.toJSONString());
                    DeepRunEventHub.publish(runId, "run_step", stepEvent.toJSONString(), false);
                } else {
                    JSONObject stepEvent = new JSONObject();
                    stepEvent.put("runId", runId);
                    stepEvent.put("eventId", eventId);
                    stepEvent.put("eventType", eventType);
                    stepEvent.put("occurredAt", occurredAt);
                    stepEvent.put("data", eventData != null ? eventData : new JSONObject());
                    DeepRunEventHub.publish(runId, "run_step", stepEvent.toJSONString(), false);
                }
            }

            if (isNew) {
                switch (eventType) {
                    case "run.started":
                        deepAgentRunService.markRunning(runId);
                        break;
                    case "plan.updated":
                        AgentRun plannedRun = deepAgentRunService.getDeepRunForReconciliation(runId);
                        planService.recordPlan(runId, plannedRun.getTaskId(), planReason(eventData),
                                eventData == null ? null : eventData.getString("summary"), dataJson);
                        break;
                    case "run.paused":
                        String pauseReason = "用户暂停或服务中断";
                        deepAgentRunService.markPausedFromCallback(runId, pauseReason);
                        planService.markPaused(runId, pauseReason);
                        break;
                    case "tool.approval.required":
                        AgentMessage approval = deepAgentRunService.createToolApproval(runId, dataJson);
                        if (approval == null) {
                            break;
                        }
                        AgentMessageVo approvalVo = new AgentMessageVo();
                        org.springframework.beans.BeanUtils.copyProperties(approval, approvalVo);
                        AgentStreamCallback approvalCallback = activeCallbacks.get(runId);
                        if (approvalCallback != null && !approvalCallback.isClosed()) {
                            approvalCallback.onQuestion(approval.getConversationId(), runId, approvalVo);
                        }
                        JSONObject question = new JSONObject();
                        question.put("conversationId", approval.getConversationId());
                        question.put("runId", runId);
                        question.put("messageId", approval.getId());
                        question.put("content", approval.getContent());
                        question.put("messageType", approval.getMessageType());
                        question.put("interactionType", approval.getInteractionType());
                        question.put("interactionStatus", approval.getInteractionStatus());
                        question.put("questionConfig", JSON.parseObject(approval.getQuestionConfig()));
                        DeepRunEventHub.publish(runId, "question", question.toJSONString(), false);
                        break;
                    case "ask_user.required":
                        AgentMessage askUser = deepAgentRunService.createAskUserQuestion(runId, dataJson);
                        AgentMessageVo askUserVo = new AgentMessageVo();
                        org.springframework.beans.BeanUtils.copyProperties(askUser, askUserVo);
                        AgentStreamCallback askUserCallback = activeCallbacks.get(runId);
                        if (askUserCallback != null && !askUserCallback.isClosed()) {
                            askUserCallback.onQuestion(askUser.getConversationId(), runId, askUserVo);
                            break;
                        }
                        JSONObject askQuestion = new JSONObject();
                        askQuestion.put("conversationId", askUser.getConversationId());
                        askQuestion.put("runId", runId);
                        askQuestion.put("messageId", askUser.getId());
                        askQuestion.put("content", askUser.getContent());
                        askQuestion.put("messageType", askUser.getMessageType());
                        askQuestion.put("interactionType", askUser.getInteractionType());
                        askQuestion.put("interactionStatus", askUser.getInteractionStatus());
                        askQuestion.put("questionConfig", JSON.parseObject(askUser.getQuestionConfig()));
                        DeepRunEventHub.publish(runId, "question", askQuestion.toJSONString(), false);
                        break;
                    case "plan.approval.required":
                        AgentMessage planApproval = deepAgentRunService.createPlanApproval(runId, dataJson);
                        if (planApproval == null) {
                            break;
                        }
                        AgentMessageVo planApprovalVo = new AgentMessageVo();
                        org.springframework.beans.BeanUtils.copyProperties(planApproval, planApprovalVo);
                        AgentStreamCallback planApprovalCallback = activeCallbacks.get(runId);
                        if (planApprovalCallback != null && !planApprovalCallback.isClosed()) {
                            planApprovalCallback.onQuestion(planApproval.getConversationId(), runId, planApprovalVo);
                            break;
                        }
                        JSONObject planQuestion = new JSONObject();
                        planQuestion.put("conversationId", planApproval.getConversationId());
                        planQuestion.put("runId", runId);
                        planQuestion.put("messageId", planApproval.getId());
                        planQuestion.put("content", planApproval.getContent());
                        planQuestion.put("messageType", planApproval.getMessageType());
                        planQuestion.put("interactionType", planApproval.getInteractionType());
                        planQuestion.put("interactionStatus", planApproval.getInteractionStatus());
                        planQuestion.put("questionConfig", JSON.parseObject(planApproval.getQuestionConfig()));
                        DeepRunEventHub.publish(runId, "question", planQuestion.toJSONString(), false);
                        break;
                    case "run.completed":
                        handleCompleted(runId, dataJson, activeCallbacks.get(runId));
                        break;
                    case "run.failed":
                        JSONObject errorData = JSON.parseObject(dataJson);
                        String errorMsg = errorData.getString("error");
                        if (deepAgentRunService.markFailed(runId, errorMsg)) {
                            notifyErrorAndRemove(runId, activeCallbacks.get(runId), 500, errorMsg);
                            DeepRunEventHub.publish(runId, "error", "{\"code\":500,\"message\":" + JSON.toJSONString(errorMsg) + "}", true);
                        }
                        break;
                    case "run.cancelled":
                        if (deepAgentRunService.markCancelled(runId)) {
                            notifyErrorAndRemove(runId, activeCallbacks.get(runId), 0, "运行已取消");
                            DeepRunEventHub.publish(runId, "error", "{\"code\":0,\"message\":\"运行已取消\"}", true);
                        }
                        break;
                }
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("回调处理失败: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 供 Deep Agent 按 agentId 实时拉取模型配置（model/baseUrl/apiKey）。
     * 接口受签名保护；apiKey 由 Deep Agent 仅在内存中使用，不写入其运行快照。
     */
    @GetMapping("/model-config/{agentId}")
    public ResponseEntity<Map<String, Object>> modelConfig(@PathVariable String agentId, HttpServletRequest request) {
        try {
            if (!verifySignature(request, new byte[0])) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            AgentDefinition agent = agentDefinitionService.getById(agentId);
            if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // 优先按直接绑定的 provider；否则按目录模型（modelId）解析，与标准 Agent 一致。
            ModelProvider provider = null;
            if (StringUtils.isNotBlank(agent.getModelProviderId())) {
                provider = modelProviderService.getById(agent.getModelProviderId());
            }
            if ((provider == null || Boolean.TRUE.equals(provider.getDeleted()))
                    && StringUtils.isNotBlank(agent.getModelId()) && modelCatalogService != null) {
                provider = modelCatalogService.resolveProvider(agent.getModelId(), "CHAT,MULTIMODAL");
            }
            if (provider == null || Boolean.TRUE.equals(provider.getDeleted()) || StringUtils.isBlank(provider.getApiKey())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("model", StringUtils.defaultIfBlank(agent.getModel(), provider.getDefaultModel()));
            result.put("baseUrl", provider.getApiBaseUrl());
            result.put("apiKey", AesUtil.decrypt(provider.getApiKey()));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("解析 Deep Agent 模型配置失败: agentId={}", agentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void handleCompleted(String runId, String dataJson, AgentStreamCallback callback) {
        JSONObject data = JSON.parseObject(dataJson);
        String content = data.getString("content");
        String model = data.getString("model");
        Integer promptTokens = integerValue(data, "prompt_tokens", "promptTokens");
        Integer completionTokens = integerValue(data, "completion_tokens", "completionTokens");
        Integer totalTokens = integerValue(data, "total_tokens", "totalTokens");
        DeepAgentRunService.CompletedRun completed = deepAgentRunService.completeRun(runId, content, model,
                promptTokens, completionTokens, totalTokens, stringValue(data, "reasoning_content", "reasoningContent"),
                integerValue(data, "reasoning_tokens", "reasoningTokens"), jsonField(data, "tool_calls", "toolCalls"),
                jsonField(data, "sources", "citations"));
        if (completed == null) {
            return;
        }
        if (callback == null || callback.isClosed()) {
            JSONObject done = new JSONObject();
            done.put("conversationId", completed.getConversationId());
            done.put("messageId", completed.getMessageId());
            done.put("content", content);
            done.put("model", model);
            done.put("promptTokens", promptTokens);
            done.put("completionTokens", completionTokens);
            done.put("totalTokens", totalTokens);
            done.put("sources", sourceList(data, "sources"));
            DeepRunEventHub.publish(runId, "done", done.toJSONString(), true);
            removeCallback(runId);
            return;
        }

        try {
            ModelStreamResponse response = new ModelStreamResponse();
            response.setContent(content);
            response.setModel(model);
            response.setPromptTokens(promptTokens);
            response.setCompletionTokens(completionTokens);
            response.setTotalTokens(totalTokens);
            response.setToolCalls(jsonField(data, "tool_calls", "toolCalls"));
            response.setSources(sourceList(data, "sources"));
            if (response.getSources() == null) {
                response.setSources(sourceList(data, "citations"));
            }
            callback.onDone(completed.getConversationId(), completed.getMessageId(), response);
            JSONObject done = new JSONObject();
            done.put("conversationId", completed.getConversationId());
            done.put("messageId", completed.getMessageId());
            done.put("content", content);
            done.put("model", model);
            done.put("promptTokens", promptTokens);
            done.put("completionTokens", completionTokens);
            done.put("totalTokens", totalTokens);
            done.put("sources", response.getSources());
            DeepRunEventHub.publish(runId, "done", done.toJSONString(), true);
        } finally {
            removeCallback(runId);
        }
    }

    private void notifyErrorAndRemove(String runId, AgentStreamCallback callback, int code, String message) {
        if (callback != null) {
            try {
                callback.onError(code, message);
            } finally {
                removeCallback(runId);
            }
        } else {
            removeCallback(runId);
        }
    }

    private Integer integerValue(JSONObject data, String snakeCaseKey, String camelCaseKey) {
        Integer value = data.getInteger(snakeCaseKey);
        return value != null ? value : data.getInteger(camelCaseKey);
    }

    private String stringValue(JSONObject data, String snakeCaseKey, String camelCaseKey) {
        String value = data.getString(snakeCaseKey);
        return value != null ? value : data.getString(camelCaseKey);
    }

    private String jsonField(JSONObject data, String snakeCaseKey, String camelCaseKey) {
        Object value = data.get(snakeCaseKey);
        if (value == null) value = data.get(camelCaseKey);
        return value != null ? JSON.toJSONString(value) : null;
    }

    private List<Map<String, Object>> sourceList(JSONObject data, String key) {
        List<Map> values = data.getList(key, Map.class);
        if (values == null) return null;
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map value : values) {
            Map<String, Object> source = new LinkedHashMap<>();
            for (Object entryObject : value.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject;
                source.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            sources.add(source);
        }
        return sources;
    }

    private boolean verifySignature(HttpServletRequest request, byte[] bodyBytes) throws Exception {
        String keyId = request.getHeader("X-Aether-Key-Id");
        String timestamp = request.getHeader("X-Aether-Timestamp");
        String signature = request.getHeader("X-Aether-Signature");
        if (keyId == null || timestamp == null || signature == null) return false;
        if (!config.getKeyId().equals(keyId)) return false;

        long ts;
        try { ts = Long.parseLong(timestamp); } catch (NumberFormatException e) { return false; }
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > MAX_SIGNATURE_AGE_SECONDS) return false;

        byte[] providedSignature = decodeHexSignature(signature);
        if (providedSignature == null) return false;

        String payload = timestamp + "." + new String(bodyBytes, StandardCharsets.UTF_8);
        return MessageDigest.isEqual(hmacSha256(config.getSharedSecret(), payload), providedSignature);
    }

    private byte[] readBodyBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private byte[] decodeHexSignature(String signature) {
        if (signature.length() != 64) return null;
        byte[] bytes = new byte[32];
        for (int i = 0; i < signature.length(); i += 2) {
            int high = Character.digit(signature.charAt(i), 16);
            int low = Character.digit(signature.charAt(i + 1), 16);
            if (high < 0 || low < 0) return null;
            bytes[i / 2] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private byte[] hmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String planReason(JSONObject data) {
        String reason = data == null ? null : data.getString("reason");
        if ("INITIAL".equals(reason) || "TOOL_RESULT".equals(reason) || "USER_INPUT".equals(reason)
                || "GOAL_CHANGED".equals(reason) || "STEP_FAILED".equals(reason)
                || "RESUME".equals(reason) || "COMPLETED".equals(reason)) {
            return reason;
        }
        return "OBSERVATION";
    }
}
