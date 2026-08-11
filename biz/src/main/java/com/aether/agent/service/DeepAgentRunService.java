package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.*;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.security.ToolCallRiskAnalyzer;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class DeepAgentRunService {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentRunService.class);
    private static final int STATUS_QUEUED = 3;
    private static final int STATUS_RUNNING = 4;
    private static final int STATUS_FAILED = 1;
    private static final int STATUS_SUCCEEDED = 0;
    private static final int STATUS_CANCELLED = 5;
    private final ToolCallRiskAnalyzer riskAnalyzer = new ToolCallRiskAnalyzer();

    private final AgentRunService agentRunService;
    private final AgentRunStepService agentRunStepService;
    private final DeepAgentSigningClient signingClient;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final AgentToolCallLogService toolCallLogService;
    private final DelegationTokenService delegationTokenService;
    private final AgentToolCatalog toolCatalog;
    private final KnowledgeContextService knowledgeContextService;
    private final SkillArtifactExecutionService artifactExecutionService;
    private final DeepAgentConfig config;

    public DeepAgentRunService(AgentRunService agentRunService,
                               AgentRunStepService agentRunStepService,
                               DeepAgentSigningClient signingClient,
                               AgentConversationService agentConversationService,
                               AgentMessageService agentMessageService,
                               AgentToolCallLogService toolCallLogService,
                               DelegationTokenService delegationTokenService,
                               AgentToolCatalog toolCatalog,
                               KnowledgeContextService knowledgeContextService,
                               SkillArtifactExecutionService artifactExecutionService,
                               DeepAgentConfig config) {
        this.agentRunService = agentRunService;
        this.agentRunStepService = agentRunStepService;
        this.signingClient = signingClient;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.toolCallLogService = toolCallLogService;
        this.delegationTokenService = delegationTokenService;
        this.toolCatalog = toolCatalog;
        this.knowledgeContextService = knowledgeContextService;
        this.artifactExecutionService = artifactExecutionService;
        this.config = config;
    }

    public String startRun(AgentDefinition agent, String userId, String conversationId,
                            String task, List<Map<String, Object>> sources) {
        return startRun(agent, userId, conversationId, task, null, null, sources, null);
    }

    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, List<Map<String, Object>> sources, Consumer<String> registerCallback) {
        return startRun(agent, userId, conversationId, task, null, null, sources, registerCallback);
    }

    /** 使用调用方已解析的 Skill 上下文创建 Deep 运行，避免在此处重新扩大工具范围。 */
    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, String attachmentContent, String attachments,
                           List<Map<String, Object>> sources, SkillRuntimeContext skillContext,
                           Consumer<String> registerCallback) {
        return startRunInternal(agent, userId, conversationId, task, attachmentContent, attachments, sources, skillContext, registerCallback);
    }

    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, String attachmentContent, String attachments,
                           List<Map<String, Object>> sources, Consumer<String> registerCallback) {
        return startRunInternal(agent, userId, conversationId, task, attachmentContent, attachments, sources, null, registerCallback);
    }

    private String startRunInternal(AgentDefinition agent, String userId, String conversationId,
                                    String task, String attachmentContent, String attachments,
                                    List<Map<String, Object>> sources, SkillRuntimeContext skillContext,
                                    Consumer<String> registerCallback) {
        AgentConversation conversation = agentConversationService.getById(conversationId);
        initializeConversationTitle(conversation, task);

        AgentMessage userMsg = new AgentMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(task);
        userMsg.setMessageType("chat");
        userMsg.setAttachmentContent(attachmentContent);
        userMsg.setAttachments(attachments);
        agentMessageService.save(userMsg);

        AgentRun run = new AgentRun();
        run.setAgentDefinitionId(agent.getId());
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setMessageId(userMsg.getId());
        run.setInputContent(truncate(task));
        run.setStatus(STATUS_QUEUED);
        run.setExecutionMode("DEEP");
        run.setModel(agent.getModel());
        if (skillContext != null) run.setSkillSnapshot(skillContext.getSnapshot());
        agentRunService.save(run);
        String runId = run.getId();
        run.setExternalRunId(runId);
        run.setRetrievalSources(JSON.toJSONString(sources == null ? Collections.emptyList() : sources));
        if (!agentRunService.updateById(run)) {
            throw new IllegalStateException("保存 Deep Agent 运行元数据失败");
        }

        try {
            if (registerCallback != null) {
                registerCallback.accept(runId);
            }
            List<AgentTool> resolvedTools = skillContext == null ? toolCatalog.getBoundTools(agent.getId()) : skillContext.getTools();
            List<String> allowedTools = resolvedTools.stream()
                    .filter(t -> t.getMcpToolName() != null)
                    .map(AgentTool::getMcpToolName)
                    .collect(Collectors.toList());
            // Managed artifact execution is a platform capability, not a user-editable
            // AgentTool. Grant it only when the already-frozen installed Skill declares it.
            if (skillContext != null && skillContext.getArtifactSkillCodes() != null
                    && !skillContext.getArtifactSkillCodes().isEmpty()) {
                allowedTools.add("generate_artifact");
            }

            List<String> artifactSkillCodes = skillContext == null || skillContext.getArtifactSkillCodes() == null
                    ? Collections.<String>emptyList() : new ArrayList<>(skillContext.getArtifactSkillCodes());
            String delegationToken = delegationTokenService.create(runId, userId, agent.getId(), allowedTools, artifactSkillCodes);

            List<Map<String, Object>> knowledgeSources = buildKnowledgeSources(sources);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("run_id", runId);
            request.put("user_id", userId);
            request.put("agent_id", agent.getId());
            request.put("conversation_id", conversationId);
            request.put("task", buildTaskContext(task, attachmentContent));
            request.put("system_prompt", skillContext == null ? (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "") : skillContext.getSystemPrompt());
            request.put("knowledge_sources", knowledgeSources);
            request.put("allowed_tools", allowedTools);
            request.put("delegation_token", delegationToken);
            if (agent.getMaxToolRounds() != null) {
                request.put("max_steps", agent.getMaxToolRounds());
            }

            ResponseEntity<String> response = signingClient.signedPost("/v1/runs", request);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                throw new RuntimeException("外部服务返回非 202: " + response.getStatusCodeValue());
            }
            log.info("Deep Agent run created: runId={}", runId);
            return runId;
        } catch (Exception e) {
            log.error("创建 Deep Agent 运行失败: runId={}", runId, e);
            markFailed(runId, e.getMessage());
            throw new RuntimeException("创建 Deep Agent 运行失败: " + e.getMessage(), e);
        }
    }

    public boolean handleCallback(String runId, String eventId, String eventType, long occurredAt, String dataJson) {
        getDeepRun(runId);
        AgentRunStep step = new AgentRunStep();
        step.setRunId(runId);
        step.setEventId(eventId);
        step.setEventType(eventType);
        step.setData(dataJson);
        step.setOccurredAt(occurredAt);
        boolean isNew = agentRunStepService.saveIfAbsent(step);
        if (isNew && eventType.startsWith("tool.")) {
            recordToolLifecycle(runId, eventType, dataJson);
        }
        return isNew;
    }

    /** 将 Deep 服务的原生工具中断转换成与普通 Agent 一致的确认交互卡片。 */
    public AgentMessage createToolApproval(String runId, String dataJson) {
        AgentRun run = getDeepRun(runId);
        JSONObject data = JSON.parseObject(dataJson);
        JSONArray actions = data.getJSONArray("actions");
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Deep tool approval has no actions");
        }
        JSONObject config = new JSONObject();
        config.put("type", "group");
        config.put("layout", "confirm");
        config.put("question", "请确认 MCP 工具调用");
        config.put("approvalType", "deep_mcp_tool_approval");
        config.put("runId", runId);
        config.put("actions", actions);
        JSONObject firstAction = actions.getJSONObject(0);
        String firstToolName = firstAction.getString("name");
        AgentTool firstTool = findBoundTool(run.getAgentDefinitionId(), firstToolName);
        JSONObject firstArguments = firstAction.getJSONObject("args");
        Map<String, Object> firstArgumentsMap = firstArguments == null ? Collections.emptyMap() : firstArguments.toJavaObject(Map.class);
        ToolCallRiskAnalyzer.Risk risk = riskAnalyzer.analyze(firstTool, firstArgumentsMap);
        config.put("toolId", firstTool == null ? null : firstTool.getId());
        config.put("toolName", firstToolName);
        config.put("arguments", firstArgumentsMap);
        config.put("riskLevel", risk.getLevel());
        config.put("riskReason", risk.getReason());
        JSONArray questions = new JSONArray();
        for (int i = 0; i < actions.size(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String toolName = action.getString("name");
            JSONObject question = new JSONObject();
            question.put("id", "decision-" + i);
            question.put("type", "choice");
            question.put("multiple", false);
            question.put("question", "high".equals(risk.getLevel())
                    ? "AI 请求执行高危 MCP 工具操作，请核对调用详情后确认。"
                    : "AI 请求调用 MCP 工具，请核对调用详情后确认。");
            question.put("options", new JSONArray()
                    .fluentAdd(new JSONObject().fluentPut("id", "once").fluentPut("label", "仅本次执行").fluentPut("value", "once"))
                    .fluentAdd(new JSONObject().fluentPut("id", "allow_10m").fluentPut("label", "当前工具 10 分钟内免确认").fluentPut("value", "allow_10m"))
                    .fluentAdd(new JSONObject().fluentPut("id", "reject").fluentPut("label", "拒绝执行").fluentPut("value", "reject")));
            questions.add(question);
        }
        config.put("questions", questions);
        AgentMessage message = new AgentMessage();
        message.setConversationId(run.getConversationId());
        message.setRole("assistant");
        message.setMessageType("interaction");
        message.setInteractionType("group");
        message.setInteractionStatus("pending");
        message.setContent(config.getString("question"));
        message.setQuestionConfig(config.toJSONString());
        if (!agentMessageService.save(message)) {
            throw new IllegalStateException("保存 Deep 工具确认消息失败");
        }
        return message;
    }

    public AgentMessage createAskUserQuestion(String runId, String dataJson) {
        AgentRun run = getDeepRun(runId);
        JSONObject data = JSON.parseObject(dataJson);
        JSONArray questions = data.getJSONArray("questions");
        if (questions == null || questions.isEmpty()) throw new IllegalArgumentException("Deep ask_user has no questions");
        JSONObject config = new JSONObject();
        config.put("type", "group"); config.put("layout", "tabs");
        config.put("question", StringUtils.defaultIfBlank(data.getString("question"), "请回答以下问题后继续"));
        config.put("approvalType", "deep_ask_user"); config.put("runId", runId); config.put("questions", questions);
        AgentMessage message = new AgentMessage();
        message.setConversationId(run.getConversationId()); message.setRole("assistant");
        message.setMessageType("interaction"); message.setInteractionType("group");
        message.setInteractionStatus("pending"); message.setContent(config.getString("question"));
        message.setQuestionConfig(config.toJSONString());
        if (!agentMessageService.save(message)) throw new IllegalStateException("保存 Deep 提问消息失败");
        return message;
    }

    /** 验证会话归属，保存确认结果并让 Deep 服务恢复同一运行。 */
    public String resumeToolApproval(String conversationId, String messageId, String userId, Map<String, Object> answer) {
        AgentMessage message = agentMessageService.getById(messageId);
        if (message == null || Boolean.TRUE.equals(message.getDeleted()) || !"pending".equals(message.getInteractionStatus())
                || !conversationId.equals(message.getConversationId())) {
            throw new IllegalArgumentException("Deep tool approval is unavailable");
        }
        JSONObject config = JSON.parseObject(message.getQuestionConfig());
        String approvalType = config.getString("approvalType");
        if (!"deep_mcp_tool_approval".equals(approvalType) && !"deep_ask_user".equals(approvalType)) {
            throw new IllegalArgumentException("Not a Deep tool approval message");
        }
        String runId = config.getString("runId");
        AgentRun run = getDeepRun(runId);
        if (!userId.equals(run.getUserId())) {
            throw new IllegalArgumentException("Deep tool approval does not belong to the current user");
        }
        if ("deep_ask_user".equals(approvalType)) {
            AgentMessage update = new AgentMessage(); update.setId(messageId); update.setInteractionStatus("answered"); update.setAnsweredAt(System.currentTimeMillis());
            agentMessageService.updateById(update);
            Map<String, Object> payload = new LinkedHashMap<>(); payload.put("run_id", runId); payload.put("answers", answer == null ? Collections.emptyMap() : answer.get("answers"));
            ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 提问恢复失败");
            return runId;
        }
        Object rawAnswers = answer == null ? null : answer.get("answers");
        if (!(rawAnswers instanceof Map)) throw new IllegalArgumentException("Tool approval answer is required");
        Map<?, ?> answers = (Map<?, ?>) rawAnswers;
        List<Map<String, String>> decisions = new ArrayList<>();
        int decisionCount = config.getJSONArray("actions").size();
        for (int i = 0; i < decisionCount; i++) {
            Object rawDecision = answers.get("decision-" + i);
            String selected = rawDecision instanceof Map ? String.valueOf(((Map<?, ?>) rawDecision).get("selected")) : "reject";
            Map<String, String> decision = new LinkedHashMap<>();
            decision.put("type", ("approve".equals(selected) || "once".equals(selected) || "allow_10m".equals(selected)) ? "approve" : "reject");
            decisions.add(decision);
        }
        AgentMessage update = new AgentMessage();
        update.setId(messageId);
        update.setInteractionStatus("answered");
        update.setAnsweredAt(System.currentTimeMillis());
        if (!agentMessageService.updateById(update)) throw new IllegalStateException("更新 Deep 工具确认状态失败");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId);
        payload.put("decisions", decisions);
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
        if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 恢复请求失败");
        return runId;
    }

    /**
     * Deep Agent 的 MCP 调用仍由 Python 执行，但审计归属平台。事件中携带的
     * toolCallId 由执行器生成，可将开始、完成和失败三个事件聚合到同一条审计记录。
     */
    private void recordToolLifecycle(String runId, String eventType, String dataJson) {
        JSONObject data = JSON.parseObject(dataJson);
        String toolCallId = data.getString("toolCallId");
        String toolName = data.getString("toolName");
        if (StringUtils.isBlank(toolCallId) || StringUtils.isBlank(toolName)) {
            log.warn("忽略缺少 toolCallId 或 toolName 的 Deep 工具事件: runId={}, type={}", runId, eventType);
            return;
        }
        AgentRun run = getDeepRun(runId);
        if ("tool.started".equals(eventType)) {
            AgentToolCallLog logEntry = new AgentToolCallLog();
            logEntry.setRunId(runId);
            logEntry.setToolCallId(toolCallId);
            logEntry.setToolName(toolName);
            logEntry.setAgentDefinitionId(run.getAgentDefinitionId());
            AgentTool tool = findBoundTool(run.getAgentDefinitionId(), toolName);
            if (tool != null) {
                logEntry.setToolId(tool.getId());
            }
            logEntry.setArguments(truncate(data.getString("arguments"), 65536));
            logEntry.setRequestMethod("MCP tools/call");
            logEntry.setRequestBody(truncate(data.getString("arguments"), 65536));
            logEntry.setStatus(4);
            toolCallLogService.save(logEntry);
            return;
        }

        AgentToolCallLog existing = toolCallLogService.getOne(Wrappers.lambdaQuery(AgentToolCallLog.class)
                .eq(AgentToolCallLog::getRunId, runId)
                .eq(AgentToolCallLog::getToolCallId, toolCallId)
                .eq(AgentToolCallLog::getDeleted, false)
                .last("LIMIT 1"));
        if (existing == null) {
            log.warn("Deep 工具完成事件没有对应的开始审计记录: runId={}, toolCallId={}", runId, toolCallId);
            return;
        }
        AgentToolCallLog update = new AgentToolCallLog();
        update.setId(existing.getId());
        update.setLatencyMs(data.getInteger("latencyMs"));
        update.setResponseBody(truncate(data.getString("outputSummary"), 65536));
        if ("tool.completed".equals(eventType)) {
            update.setStatus(0);
        } else {
            update.setStatus(1);
            update.setErrorMsg(truncate(data.getString("error"), 2048));
        }
        toolCallLogService.updateById(update);
    }

    private AgentTool findBoundTool(String agentId, String toolName) {
        if (StringUtils.isBlank(toolName)) {
            return null;
        }
        return toolCatalog.getBoundTools(agentId).stream()
                .filter(tool -> toolName.equals(tool.getMcpToolName()) || toolName.equals(tool.getName()))
                .findFirst().orElse(null);
    }

    private String buildTaskContext(String task, String attachmentContent) {
        if (StringUtils.isBlank(attachmentContent)) {
            return task;
        }
        return StringUtils.defaultString(task) + "\n\n附件内容：\n" + attachmentContent;
    }

    @Transactional(rollbackFor = Exception.class)
    public CompletedRun completeRun(String runId, String content, String model,
                                     Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                     String reasoningContent, Integer reasoningTokens, String toolCalls, String citations) {
        AgentRun run = getDeepRun(runId);
        boolean claimed = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_SUCCEEDED)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (!claimed) {
            return null;
        }
        AgentMessage message = new AgentMessage();
        message.setConversationId(run.getConversationId());
        message.setRole("assistant");
        message.setMessageType("chat");
        message.setContent(content);
        message.setReasoningContent(reasoningContent);
        message.setReasoningTokens(reasoningTokens);
        message.setToolCalls(toolCalls);
        message.setCitations(citations);
        message.setModel(model);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        if (!agentMessageService.save(message)) {
            throw new IllegalStateException("保存 Deep Agent 最终消息失败");
        }

        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getMessageId, message.getId())
                .set(AgentRun::getOutputContent, truncate(content))
                .set(AgentRun::getModel, model)
                .set(AgentRun::getPromptTokens, promptTokens)
                .set(AgentRun::getCompletionTokens, completionTokens)
                .set(AgentRun::getTotalTokens, totalTokens)
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getStatus, STATUS_SUCCEEDED));
        if (!updated) {
            throw new IllegalStateException("更新 Deep Agent 最终运行记录失败");
        }
        artifactExecutionService.attachPendingArtifacts(runId, message.getId());
        agentConversationService.update(null, Wrappers.lambdaUpdate(AgentConversation.class)
                .eq(AgentConversation::getId, run.getConversationId())
                .setSql("message_count = message_count + 1"));
        recordKnowledgeOutcomeAfterCommit(run, message);
        return new CompletedRun(run.getConversationId(), message.getId());
    }

    public boolean markRunning(String runId) {
        return agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_RUNNING)
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getStatus, STATUS_QUEUED));
    }

    public boolean markSucceeded(String runId, String content, String model,
                                 Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_SUCCEEDED)
                .set(AgentRun::getOutputContent, truncate(content))
                .set(AgentRun::getModel, model)
                .set(AgentRun::getPromptTokens, promptTokens)
                .set(AgentRun::getCompletionTokens, completionTokens)
                .set(AgentRun::getTotalTokens, totalTokens)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
    }

    public boolean markFailed(String runId, String errorMsg) {
        return agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_FAILED)
                .set(AgentRun::getErrorMsg, truncate(errorMsg))
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
    }

    public boolean markCancelled(String runId) {
        return agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_CANCELLED)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
    }

    public AgentRun getDeepRunForReconciliation(String runId) {
        return getDeepRun(runId);
    }

    private List<Map<String, Object>> buildKnowledgeSources(List<Map<String, Object>> sources) {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> src : sources) {
            Map<String, Object> ks = new LinkedHashMap<>();
            String title = stringValue(src.get("documentName"));
            if (StringUtils.isBlank(title)) title = stringValue(src.get("documentTitle"));
            if (StringUtils.isBlank(title)) title = "知识库文档 " + (result.size() + 1);
            String content = stringValue(src.get("content"));
            Integer citationIndex = src.get("citationIndex") instanceof Integer ? (Integer) src.get("citationIndex") : null;
            ks.put("title", title != null ? title : "");
            // 保留与 Dashboard 知识库引用模型一致的字段名，Deep 服务完成事件会原样回传。
            ks.put("documentName", title);
            ks.put("documentId", stringValue(src.get("documentId")));
            ks.put("chunkId", stringValue(src.get("chunkId")));
            ks.put("sectionPath", stringValue(src.get("sectionPath")));
            ks.put("content", content != null ? content : "");
            ks.put("similarity", src.get("similarity"));
            ks.put("retrievalScore", src.get("retrievalScore"));
            ks.put("citationIndex", citationIndex);
            ks.put("citation", citationIndex != null ? "【" + citationIndex + "】" : "");
            result.add(ks);
        }
        return result;
    }

    private String truncate(String value) {
        return value == null || value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** Deep runs are submitted after the frontend creates an untitled conversation. */
    private void initializeConversationTitle(AgentConversation conversation, String task) {
        if (conversation == null || StringUtils.isNotBlank(conversation.getTitle())) {
            return;
        }
        String title = StringUtils.defaultString(task).trim();
        if (StringUtils.isBlank(title)) {
            return;
        }
        agentConversationService.update(null, Wrappers.lambdaUpdate(AgentConversation.class)
                .set(AgentConversation::getTitle, title.length() > 50 ? title.substring(0, 50) : title)
                .eq(AgentConversation::getId, conversation.getId()));
    }

    private String stringValue(Object obj) {
        return obj == null ? null : obj.toString();
    }

    private void recordKnowledgeOutcomeAfterCommit(AgentRun run, AgentMessage message) {
        List<Map<String, Object>> retrievedSources = deserializeSources(run.getRetrievalSources());
        ModelStreamResponse response = new ModelStreamResponse();
        response.setContent(message.getContent());
        List<Map<String, Object>> citedSources = knowledgeContextService.ensureCitations(response, retrievedSources);
        Runnable record = () -> {
            knowledgeContextService.recordCitations(run.getAgentDefinitionId(), run.getConversationId(), message.getId(), citedSources);
            knowledgeContextService.recordRetrievalOutcome(run.getAgentDefinitionId(), run.getConversationId(), message.getId(),
                    run.getInputContent(), retrievedSources, citedSources);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    private List<Map<String, Object>> deserializeSources(String serializedSources) {
        if (serializedSources == null) {
            return Collections.emptyList();
        }
        try {
            JSONArray values = JSON.parseArray(serializedSources);
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> sources = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Map) {
                    Map<String, Object> source = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                        source.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    sources.add(source);
                }
            }
            return sources;
        } catch (Exception e) {
            log.warn("Deep Agent retrieval source metadata is invalid");
            return Collections.emptyList();
        }
    }

    private AgentRun getDeepRun(String runId) {
        AgentRun run = agentRunService.getById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !"DEEP".equals(run.getExecutionMode())) {
            throw new IllegalArgumentException("Unknown Deep Agent run: " + runId);
        }
        return run;
    }

    public static class CompletedRun {
        private final String conversationId;
        private final String messageId;

        public CompletedRun(String conversationId, String messageId) {
            this.conversationId = conversationId;
            this.messageId = messageId;
        }

        public String getConversationId() { return conversationId; }
        public String getMessageId() { return messageId; }
    }
}
