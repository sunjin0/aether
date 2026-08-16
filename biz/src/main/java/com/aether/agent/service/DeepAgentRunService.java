package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.*;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.security.ToolCallRiskAnalyzer;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.service.AdminPreferenceService;
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
    private static final int STATUS_PAUSED = 6;
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
    private final ConversationContextService conversationContextService;
    private final AgentSessionService agentSessionService;
    private final AgentTaskService agentTaskService;
    private final AgentTaskEventService agentTaskEventService;
    private final AgentSessionMemoryService agentSessionMemoryService;
    private final AdminPreferenceService adminPreferenceService;
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
                               ConversationContextService conversationContextService,
                               AgentSessionService agentSessionService,
                               AgentTaskService agentTaskService,
                               AgentTaskEventService agentTaskEventService,
                               AgentSessionMemoryService agentSessionMemoryService,
                               AdminPreferenceService adminPreferenceService,
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
        this.conversationContextService = conversationContextService;
        this.agentSessionService = agentSessionService;
        this.agentTaskService = agentTaskService;
        this.agentTaskEventService = agentTaskEventService;
        this.agentSessionMemoryService = agentSessionMemoryService;
        this.adminPreferenceService = adminPreferenceService;
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
        AgentSession session = agentSessionService == null ? null
                : agentSessionService.getOrCreate(conversationId, userId, agent.getId());
        String sessionId = session == null ? conversationId : session.getId();
        TaskRoute route = resolveTaskRoute(sessionId, task);
        AgentTask taskRecord = route.reuseTask || agentTaskService == null ? route.activeTask
                : agentTaskService.create(sessionId, userId, agent.getId(), task);
        // Read durable history before saving this request's user message so that
        // the current task is supplied only once to the Deep Agent.
        List<Map<String, String>> conversationMemory = buildConversationMemory(conversationId, sessionId, userId, task);
        if (route.reuseTask && taskRecord != null) {
            // 延续同一任务：注入任务身份提示，避免 Deep Agent 当作全新任务重新开始而丢失目标上下文。
            conversationMemory.add(continuationContextHint(taskRecord));
        }

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
        run.setSessionId(sessionId);
        run.setTaskId(taskRecord == null ? null : taskRecord.getId());
        run.setAttemptNo(taskRecord == null ? 1 : nextAttemptNo(taskRecord.getId()));
        run.setMessageId(userMsg.getId());
        // Replaced with the outbound Deep Agent request snapshot before dispatch.
        run.setInputContent(task);
        run.setStatus(STATUS_QUEUED);
        run.setExecutionMode("DEEP");
        run.setModel(agent.getModel());
        run.setSkillSnapshot(snapshotWithToolApprovalPolicy(skillContext, conversation));
        agentRunService.save(run);
        String runId = run.getId();
        boolean dispatchImmediately = taskRecord == null || agentSessionService == null || route.reuseTask
                || agentSessionService.claimTask(sessionId, taskRecord.getId());
        if (taskRecord != null) {
            agentTaskService.updateStatus(taskRecord.getId(), dispatchImmediately ? "RUNNING" : "QUEUED", runId,
                    dispatchImmediately ? null : "等待当前任务完成");
            if (dispatchImmediately && agentSessionService != null) {
                agentSessionService.updateTaskState(sessionId, taskRecord.getId(), "RUNNING");
            }
            if (agentTaskEventService != null) {
                agentTaskEventService.record(taskRecord.getId(), runId, "task.routed",
                        route.eventSummary());
            }
        }
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
            // 文件生成由已绑定的通用 generate_artifact 工具授权；Skill 仅影响本轮提示词规范，
            // 不再通过委派令牌选择脚本或模板。
            String delegationToken = delegationTokenService.create(runId, userId, agent.getId(), allowedTools);

            List<Map<String, Object>> knowledgeSources = buildKnowledgeSources(sources);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("run_id", runId);
            request.put("user_id", userId);
            request.put("agent_id", agent.getId());
            request.put("conversation_id", conversationId);
            request.put("session_id", sessionId);
            request.put("task_id", run.getTaskId());
            request.put("task", buildTaskContext(task, attachmentContent));
            Map<String, Object> taskState = new LinkedHashMap<>();
            taskState.put("status", "RUNNING");
            taskState.put("title", taskRecord == null ? task : taskRecord.getTitle());
            taskState.put("attempt", run.getAttemptNo());
            taskState.put("routing", route.name());
            request.put("task_state", taskState);
            request.put("conversation_memory", conversationMemory);
            request.put("system_prompt", skillContext == null ? (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "") : skillContext.getSystemPrompt());
            request.put("knowledge_sources", knowledgeSources);
            request.put("allowed_tools", allowedTools);
            request.put("tool_approval_policy", readToolApprovalPolicy(run.getSkillSnapshot()));
            request.put("delegation_token", delegationToken);
            // 计划先行：生成初始计划后等待用户确认再执行（Codex/Claude 风格）。
            request.put("plan_approval_required", true);
            if (agent.getMaxToolRounds() != null) {
                request.put("max_steps", agent.getMaxToolRounds());
            }
            AgentRun inputUpdate = new AgentRun();
            inputUpdate.setId(runId);
            inputUpdate.setInputContent(deepAgentInputSnapshot(request));
            if (!agentRunService.updateById(inputUpdate)) {
                throw new IllegalStateException("保存 Deep Agent 输入快照失败");
            }

            if (!dispatchImmediately) {
                if (agentTaskEventService != null) {
                    agentTaskEventService.record(run.getTaskId(), runId, "task.queued", "当前会话已有任务正在处理");
                }
                return runId;
            }

            // 延续同一任务时，若其前序 run 仍在执行（中断聊天流并不会取消运行），先让 Deep Agent
            // 暂停该图线程，避免本次延续与旧 run 并发改写同一会话上下文导致信息丢失。
            if (route.reuseTask && taskRecord != null && StringUtils.isNotBlank(taskRecord.getCurrentRunId())
                    && !taskRecord.getCurrentRunId().equals(runId)) {
                settleActiveRunBeforeContinuation(taskRecord.getCurrentRunId());
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

    private List<Map<String, String>> buildConversationMemory(String conversationId, String sessionId, String userId, String currentTask) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        addConfirmedPreferences(result, userId);
        if (agentSessionMemoryService != null && StringUtils.isNotBlank(sessionId)) {
            List<AgentSessionMemory> memories = agentSessionMemoryService.listInjectable(sessionId, 12);
            if (memories != null) {
                memories = new ArrayList<AgentSessionMemory>(memories);
                memories.sort((left, right) -> Integer.compare(memoryRelevance(right, currentTask), memoryRelevance(left, currentTask)));
            }
            if (memories != null) for (AgentSessionMemory memory : memories.subList(0, Math.min(4, memories.size()))) {
                if (StringUtils.isBlank(memory.getContent())) continue;
                Map<String, String> item = new LinkedHashMap<String, String>();
                item.put("role", "system");
                item.put("content", "【已完成任务结论】" + memory.getContent());
                result.add(item);
            }
        }
        if (conversationContextService != null && StringUtils.isNotBlank(conversationId)) {
            List<ModelChatMessage> messages = conversationContextService.buildDeepSessionMemory(conversationId);
            if (messages != null) for (ModelChatMessage message : messages) {
                if (message == null || StringUtils.isBlank(message.getContent())) continue;
                String role = message.getRole();
                if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) continue;
                Map<String, String> item = new LinkedHashMap<String, String>();
                item.put("role", role);
                item.put("content", message.getContent());
                result.add(item);
            }
        }
        return result;
    }

    /** 不跨越 Session 权限边界，仅在已允许注入的记忆集合中按当前任务做轻量相关性排序。 */
    private int memoryRelevance(AgentSessionMemory memory, String currentTask) {
        if (memory == null) return Integer.MIN_VALUE;
        String content = StringUtils.defaultString(memory.getContent()).toLowerCase(Locale.ROOT);
        int score = memory.getImportance() == null ? 0 : memory.getImportance();
        if (StringUtils.isBlank(currentTask) || StringUtils.isBlank(content)) return score;
        String normalized = currentTask.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]+", " ").trim();
        for (String word : normalized.split("\\s+")) {
            if (word.length() >= 2 && content.contains(word)) score += 30;
        }
        String han = normalized.replaceAll("[^\\p{IsHan}]", "");
        for (int index = 0; index + 1 < han.length(); index++) {
            if (content.contains(han.substring(index, index + 2))) score += 8;
        }
        return score;
    }

    private void addConfirmedPreferences(List<Map<String, String>> target, String userId) {
        if (adminPreferenceService == null || StringUtils.isBlank(userId)) return;
        List<AdminPreference> preferences = adminPreferenceService.list(Wrappers.lambdaQuery(AdminPreference.class)
                .eq(AdminPreference::getAdminId, userId)
                .eq(AdminPreference::getSource, AdminPreference.SOURCE_EXPLICIT)
                .eq(AdminPreference::getStatus, AdminPreference.STATUS_ENABLED)
                .eq(AdminPreference::getDeleted, false)
                .orderByDesc(AdminPreference::getEffectiveScore)
                .last("limit 8"));
        if (preferences == null || preferences.isEmpty()) return;
        StringBuilder content = new StringBuilder("【用户已确认偏好】");
        for (AdminPreference preference : preferences) {
            if (StringUtils.isBlank(preference.getValue())) continue;
            content.append("\n- ").append(StringUtils.defaultIfBlank(preference.getKeyName(), preference.getCategory()))
                    .append("：").append(StringUtils.abbreviate(preference.getValue(), 300));
        }
        if (content.length() > "【用户已确认偏好】".length()) {
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("role", "system");
            item.put("content", content.toString());
            target.add(item);
        }
    }

    private String snapshotWithToolApprovalPolicy(SkillRuntimeContext skillContext, AgentConversation conversation) {
        JSONObject snapshot = skillContext == null || StringUtils.isBlank(skillContext.getSnapshot())
                ? new JSONObject() : JSON.parseObject(skillContext.getSnapshot());
        snapshot.put("toolApprovalPolicy", normalizeToolApprovalPolicy(conversation == null ? null : conversation.getToolApprovalPolicy()));
        return snapshot.toJSONString();
    }

    /** Keep the actual Deep Agent payload inspectable, but never persist its delegation credential. */
    private String deepAgentInputSnapshot(Map<String, Object> request) {
        Map<String, Object> snapshot = new LinkedHashMap<>(request);
        snapshot.remove("delegation_token");
        return JSON.toJSONString(snapshot);
    }

    private String readToolApprovalPolicy(String snapshotJson) {
        try { return normalizeToolApprovalPolicy(JSON.parseObject(snapshotJson).getString("toolApprovalPolicy")); }
        catch (Exception ignored) { return "ask"; }
    }

    private String normalizeToolApprovalPolicy(String policy) {
        return "risky".equals(policy) || "never".equals(policy) ? policy : "ask";
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
        validateActionsInRunScope(run, actions);
        if ("risky".equals(readToolApprovalPolicy(run.getSkillSnapshot())) && !containsHighRiskAction(run, actions)) {
            resumeDeepToolDecisions(runId, approveAll(actions.size()));
            return null;
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
        ToolCallRiskAnalyzer.Risk risk = firstTool == null ? new ToolCallRiskAnalyzer.Risk("high", "工具不在当前授权范围内", null) : riskAnalyzer.analyze(firstTool, firstArgumentsMap);
        config.put("toolId", firstTool == null ? null : firstTool.getId());
        config.put("toolName", firstToolName);
        config.put("arguments", firstArgumentsMap);
        config.put("riskLevel", risk.getLevel());
        config.put("riskReason", risk.getReason());
        config.put("riskEvidence", risk.getEvidence());
        JSONArray questions = new JSONArray();
        for (int i = 0; i < actions.size(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String toolName = action.getString("name");
            AgentTool actionTool = findBoundTool(run.getAgentDefinitionId(), toolName);
            JSONObject actionArguments = action.getJSONObject("args");
            Map<String, Object> actionArgumentsMap = actionArguments == null ? Collections.emptyMap() : actionArguments.toJavaObject(Map.class);
            ToolCallRiskAnalyzer.Risk actionRisk = actionTool == null ? new ToolCallRiskAnalyzer.Risk("high", "工具不在当前授权范围内", null) : riskAnalyzer.analyze(actionTool, actionArgumentsMap);
            JSONObject question = new JSONObject();
            question.put("id", "decision-" + i);
            question.put("type", "choice");
            question.put("multiple", false);
            question.put("question", "high".equals(actionRisk.getLevel())
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
        updateTaskStatus(run, "WAITING_APPROVAL", "等待工具调用审批");
        return message;
    }

    private boolean containsHighRiskAction(AgentRun run, JSONArray actions) {
        for (int i = 0; i < actions.size(); i++) {
            JSONObject action = actions.getJSONObject(i);
            AgentTool tool = findBoundTool(run.getAgentDefinitionId(), action.getString("name"));
            if (tool == null) return true;
            JSONObject args = action.getJSONObject("args");
            Map<String, Object> arguments = args == null ? Collections.emptyMap() : args.toJavaObject(Map.class);
            if ("high".equals(riskAnalyzer.analyze(tool, arguments).getLevel())) return true;
        }
        return false;
    }

    private List<Map<String, String>> approveAll(int count) {
        List<Map<String, String>> decisions = new ArrayList<>();
        for (int i = 0; i < count; i++) decisions.add(Collections.singletonMap("type", "approve"));
        return decisions;
    }

    private void resumeDeepToolDecisions(String runId, List<Map<String, String>> decisions) {
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("run_id", runId); payload.put("decisions", decisions);
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
        if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 自动批准恢复失败");
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
        updateTaskStatus(run, "WAITING_USER", "等待用户补充信息");
        return message;
    }

    /** 将 Deep 服务的计划确认请求转换成交互卡片，等待用户批准后再执行。 */
    public AgentMessage createPlanApproval(String runId, String dataJson) {
        AgentRun run = getDeepRun(runId);
        JSONObject data = JSON.parseObject(dataJson);
        JSONArray plan = data.getJSONArray("plan");
        if (plan == null || plan.isEmpty()) {
            throw new IllegalArgumentException("Deep plan approval has no plan");
        }
        JSONObject config = new JSONObject();
        config.put("type", "group");
        config.put("layout", "confirm");
        config.put("question", "请确认执行计划，批准后将按计划执行。");
        config.put("approvalType", "deep_plan_approval");
        config.put("runId", runId);
        config.put("plan", plan);
        if (StringUtils.isNotBlank(data.getString("document"))) {
            config.put("document", data.getString("document"));
        }
        AgentMessage message = new AgentMessage();
        message.setConversationId(run.getConversationId());
        message.setRole("assistant");
        message.setMessageType("interaction");
        message.setInteractionType("group");
        message.setInteractionStatus("pending");
        message.setContent(config.getString("question"));
        message.setQuestionConfig(config.toJSONString());
        if (!agentMessageService.save(message)) {
            throw new IllegalStateException("保存 Deep 计划确认消息失败");
        }
        updateTaskStatus(run, "WAITING_APPROVAL", "等待计划确认");
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
        if (!"deep_mcp_tool_approval".equals(approvalType) && !"deep_ask_user".equals(approvalType)
                && !"deep_plan_approval".equals(approvalType)) {
            throw new IllegalArgumentException("Not a Deep tool approval message");
        }
        String runId = config.getString("runId");
        AgentRun run = getDeepRun(runId);
        if (!userId.equals(run.getUserId())) {
            throw new IllegalArgumentException("Deep tool approval does not belong to the current user");
        }
        if ("deep_plan_approval".equals(approvalType)) {
            AgentMessage update = new AgentMessage();
            update.setId(messageId);
            update.setInteractionStatus("answered");
            update.setAnsweredAt(System.currentTimeMillis());
            agentMessageService.updateById(update);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("run_id", runId);
            payload.put("plan_approved", true);
            ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 计划确认恢复失败");
            updateTaskStatus(run, "RUNNING", null);
            return runId;
        }
        if ("deep_ask_user".equals(approvalType)) {
            AgentMessage update = new AgentMessage(); update.setId(messageId); update.setInteractionStatus("answered"); update.setAnsweredAt(System.currentTimeMillis());
            agentMessageService.updateById(update);
            Map<String, Object> payload = new LinkedHashMap<>(); payload.put("run_id", runId); payload.put("answers", answer == null ? Collections.emptyMap() : answer.get("answers"));
            ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 提问恢复失败");
            updateTaskStatus(run, "RUNNING", null);
            return runId;
        }
        Object rawAnswers = answer == null ? null : answer.get("answers");
        if (!(rawAnswers instanceof Map)) throw new IllegalArgumentException("Tool approval answer is required");
        Map<?, ?> answers = (Map<?, ?>) rawAnswers;
        List<Map<String, String>> decisions = new ArrayList<>();
        JSONArray actions = config.getJSONArray("actions");
        int decisionCount = actions.size();
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
        updateTaskStatus(run, "RUNNING", null);
        return runId;
    }

    public void pause(String runId, String userId) {
        AgentRun run = getDeepRun(runId);
        if (!userId.equals(run.getUserId())) throw new IllegalArgumentException("Deep run does not belong to the current user");
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/pause", Collections.emptyMap());
        if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 暂停请求失败");
        AgentRun update = new AgentRun(); update.setId(runId); update.setStatus(6); agentRunService.updateById(update);
        updateTaskStatus(run, "PAUSED", "用户暂停");
    }

    /** Deep Runtime 重启或连接中断时的回调投影，不再向外部重复发送暂停请求。 */
    public boolean markPausedFromCallback(String runId, String reason) {
        AgentRun run = getDeepRun(runId);
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, 6)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (updated) updateTaskStatus(run, "PAUSED", reason);
        return updated;
    }

    public void resume(String runId, String userId) {
        AgentRun run = getDeepRun(runId);
        if (!userId.equals(run.getUserId())) throw new IllegalArgumentException("Deep run does not belong to the current user");
        if (!Integer.valueOf(6).equals(run.getStatus())) throw new IllegalArgumentException("Deep run is not paused");
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", Collections.emptyMap());
        if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 继续请求失败");
        AgentRun update = new AgentRun(); update.setId(runId); update.setStatus(3); agentRunService.updateById(update);
        updateTaskStatus(run, "RUNNING", null);
    }

    /** Re-check live binding and the immutable run scope before resuming a paused external tool call. */
    private void validateActionsInRunScope(AgentRun run, JSONArray actions) {
        Set<String> toolIds;
        try {
            JSONArray ids = JSON.parseObject(run.getSkillSnapshot()).getJSONArray("toolIds");
            toolIds = ids == null ? Collections.<String>emptySet() : new HashSet<>(ids.toJavaList(String.class));
        } catch (Exception e) {
            throw new IllegalStateException("Deep 工具确认缺少运行授权范围");
        }
        Map<String, AgentTool> liveTools = toolCatalog.getBoundTools(run.getAgentDefinitionId()).stream()
                .collect(Collectors.toMap(AgentTool::getMcpToolName, item -> item, (left, right) -> left));
        for (int i = 0; i < actions.size(); i++) {
            AgentTool tool = liveTools.get(actions.getJSONObject(i).getString("name"));
            if (tool == null || !toolIds.contains(tool.getId())) {
                throw new IllegalStateException("待确认工具已不可用或不在本次运行授权范围内");
            }
        }
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
                .set(AgentRun::getOutputContent, content)
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
        if (agentSessionMemoryService != null) {
            agentSessionMemoryService.recordTaskConclusion(run.getSessionId(), run.getTaskId(), runId, content);
        }
        updateTaskStatus(run, "COMPLETED", null);
        return new CompletedRun(run.getConversationId(), message.getId());
    }

    public boolean markRunning(String runId) {
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_RUNNING)
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getStatus, STATUS_QUEUED));
        if (updated) updateTaskStatus(getDeepRun(runId), "RUNNING", null);
        return updated;
    }

    public boolean markSucceeded(String runId, String content, String model,
                                 Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_SUCCEEDED)
                .set(AgentRun::getOutputContent, content)
                .set(AgentRun::getModel, model)
                .set(AgentRun::getPromptTokens, promptTokens)
                .set(AgentRun::getCompletionTokens, completionTokens)
                .set(AgentRun::getTotalTokens, totalTokens)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
    }

    public boolean markFailed(String runId, String errorMsg) {
        // 外部创建请求失败时，刚写入的运行记录可能尚不可重新读取；失败状态
        // 仍必须优先落库，Task 投影则在能取得运行记录时再同步。
        // error_msg 列为 varchar(1024)，超长错误会触发 "value too long"。
        String safeError = StringUtils.abbreviate(errorMsg, 1024);
        AgentRun run = null;
        try {
            run = getDeepRun(runId);
        } catch (IllegalArgumentException ignored) {
            log.warn("无法读取失败的 Deep Agent 运行记录: runId={}", runId);
        }
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_FAILED)
                .set(AgentRun::getErrorMsg, safeError)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (updated) updateTaskStatus(run, "FAILED", safeError);
        return updated;
    }

    public boolean markCancelled(String runId) {
        AgentRun run = getDeepRun(runId);
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_CANCELLED)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (updated) updateTaskStatus(run, "CANCELLED", "用户取消");
        return updated;
    }

    private void updateTaskStatus(AgentRun run, String status, String reason) {
        if (run != null && agentTaskService != null) {
            agentTaskService.updateStatus(run.getTaskId(), status, run.getId(), reason);
            if (agentSessionService != null) agentSessionService.updateTaskState(run.getSessionId(), run.getTaskId(), status);
            if (agentTaskEventService != null) agentTaskEventService.record(run.getTaskId(), run.getId(), "task.status_changed", status + (StringUtils.isBlank(reason) ? "" : "：" + reason));
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                dispatchNextQueuedAfterCommit(run.getSessionId());
            }
        }
    }

    /** Start exactly one queued task after the active task has durably released the Session. */
    private void dispatchNextQueuedAfterCommit(String sessionId) {
        if (StringUtils.isBlank(sessionId) || agentTaskService == null || agentSessionService == null) return;
        Runnable dispatch = () -> {
            AgentTask next = agentTaskService.nextQueued(sessionId);
            if (next == null || !agentSessionService.claimTask(sessionId, next.getId())) return;
            AgentRun nextRun = agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                    .eq(AgentRun::getTaskId, next.getId())
                    .eq(AgentRun::getExecutionMode, "DEEP")
                    .eq(AgentRun::getStatus, STATUS_QUEUED)
                    .eq(AgentRun::getDeleted, false)
                    .orderByAsc(AgentRun::getCreatedAt).last("limit 1"));
            if (nextRun == null) {
                agentTaskService.updateStatus(next.getId(), "FAILED", null, "缺少排队运行快照");
                agentSessionService.updateTaskState(sessionId, next.getId(), "FAILED");
                return;
            }
            try {
                JSONObject request = JSON.parseObject(nextRun.getInputContent());
                if (request == null) throw new IllegalStateException("排队运行输入快照无效");
                JSONArray allowedTools = request.getJSONArray("allowed_tools");
                List<String> toolNames = allowedTools == null ? Collections.<String>emptyList()
                        : allowedTools.toJavaList(String.class);
                request.put("delegation_token", delegationTokenService.create(nextRun.getId(), nextRun.getUserId(),
                        nextRun.getAgentDefinitionId(), toolNames));
                agentTaskService.updateStatus(next.getId(), "RUNNING", nextRun.getId(), null);
                agentSessionService.updateTaskState(sessionId, next.getId(), "RUNNING");
                ResponseEntity<String> response = signingClient.signedPost("/v1/runs", request);
                if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                    throw new IllegalStateException("排队 Deep Agent 请求失败: " + response.getStatusCodeValue());
                }
                if (agentTaskEventService != null) {
                    agentTaskEventService.record(next.getId(), nextRun.getId(), "task.dispatched", "已从会话队列开始执行");
                }
            } catch (Exception error) {
                log.error("派发排队 Deep Agent 任务失败: taskId={}", next.getId(), error);
                markFailed(nextRun.getId(), error.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch.run(); }
            });
        } else {
            dispatch.run();
        }
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

    /**
     * 延续同一任务前，若其前序 run 仍在 QUEUED/RUNNING（例如用户中断聊天流但运行未被取消），
     * 先通知 Deep Agent 暂停该 LangGraph 线程并落库暂停态。检查点保留计划与已执行步骤，
     * 后续延续 run 恢复同一线程时上下文不丢失。暂停失败不阻断延续——若运行实际已结束则无并发。
     */
    private void settleActiveRunBeforeContinuation(String previousRunId) {
        try {
            AgentRun previous = agentRunService.getById(previousRunId);
            if (previous == null || Boolean.TRUE.equals(previous.getDeleted()) || !"DEEP".equals(previous.getExecutionMode())
                    || !(Integer.valueOf(STATUS_QUEUED).equals(previous.getStatus())
                    || Integer.valueOf(STATUS_RUNNING).equals(previous.getStatus()))) {
                return;
            }
            ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + previousRunId + "/pause", Collections.emptyMap());
            if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                log.warn("延续前暂停前序运行未获受理: runId={}, status={}", previousRunId, response.getStatusCodeValue());
                return;
            }
            AgentRun update = new AgentRun();
            update.setId(previousRunId);
            update.setStatus(STATUS_PAUSED);
            agentRunService.updateById(update);
        } catch (Exception e) {
            log.warn("延续任务前暂停前序运行失败: runId={}", previousRunId, e);
        }
    }

    /** 延续同一任务时注入的身份提示，帮助 Deep Agent 沿用任务目标而非重新规划。 */
    private Map<String, String> continuationContextHint(AgentTask taskRecord) {
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("role", "system");
        item.put("content", "【当前任务继续】你正在继续会话中的任务「"
                + StringUtils.defaultString(taskRecord.getTitle(), "当前任务")
                + "」。请结合该任务已完成的步骤与已有上下文继续推进；如需调整目标，请依据用户最新指示修订计划，不要当作全新任务重新开始。");
        return item;
    }

    /**
     * 将后续消息先归入当前 Session，而不是机械地把每句话都当成独立任务。
     * 仅在当前任务已暂停且用户给出明确延续或改目标指令时复用 Task；运行中、排队中
     * 的任务仍按串行队列处理，避免两次 Deep 调用并发修改同一图线程。
     */
    private TaskRoute resolveTaskRoute(String sessionId, String input) {
        if (agentTaskService == null) return TaskRoute.NEW_TASK;
        AgentTask active = agentTaskService.findActive(sessionId);
        if (active == null) return TaskRoute.NEW_TASK;
        String normalized = StringUtils.defaultString(input).trim().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "改为", "改成", "换成", "调整为", "不再", "取消原", "变更目标", "重新", "重做")) {
            return TaskRoute.goalChanged(active);
        }
        // 会话内只有一个任务：其余消息都视为对其计划的继续/补充（含暂停后继续）。
        return TaskRoute.continueTask(active);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private int nextAttemptNo(String taskId) {
        if (StringUtils.isBlank(taskId)) return 1;
        AgentRun latestRun = agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getTaskId, taskId).eq(AgentRun::getExecutionMode, "DEEP")
                .eq(AgentRun::getDeleted, false).orderByDesc(AgentRun::getAttemptNo).last("limit 1"), false);
        return latestRun == null || latestRun.getAttemptNo() == null ? 1 : latestRun.getAttemptNo() + 1;
    }

    private static final class TaskRoute {
        private static final TaskRoute NEW_TASK = new TaskRoute("NEW_TASK", null, false, "创建新的会话任务");
        private final String type;
        private final AgentTask activeTask;
        private final boolean reuseTask;
        private final String summary;

        private TaskRoute(String type, AgentTask activeTask, boolean reuseTask, String summary) {
            this.type = type; this.activeTask = activeTask; this.reuseTask = reuseTask; this.summary = summary;
        }
        private static TaskRoute continueTask(AgentTask task) {
            return new TaskRoute("CONTINUE", task, true, "识别为对暂停任务的明确补充或继续");
        }
        private static TaskRoute goalChanged(AgentTask task) {
            return new TaskRoute("GOAL_CHANGED", task, true, "识别为对暂停任务的目标变更");
        }
        private String name() { return type; }
        private String eventSummary() { return summary; }
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
