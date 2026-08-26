package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.*;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.vo.AgentRunPlanVo;
import com.aether.agent.security.ToolCallRiskAnalyzer;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.User;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.sys.service.UserService;
import com.aether.utils.AesUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 定义Deep智能体运行业务服务契约。
 */
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
    private final AgentRunPlanService planService;
    private final ToolRouterService toolRouterService;
    private final DeepAgentConfig config;

    /** Optional to preserve direct construction used by legacy unit tests. */
    @Autowired(required = false)
    private CapabilityIndexService capabilityIndexService;
    @Autowired(required = false)
    private ContextMetricService contextMetricService;
    @Autowired(required = false)
    private RuntimeEmailCredentialStore runtimeEmailCredentialStore;
    @Autowired(required = false)
    private EmailCredentialTokenService emailCredentialTokenService;
    @Autowired(required = false)
    private UserService userService;

    /**
     * 创建 {@code DeepAgentRunService} 实例。
     */
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
                               AgentRunPlanService planService,
                               ToolRouterService toolRouterService,
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
        this.planService = planService;
        this.toolRouterService = toolRouterService;
        this.config = config;
    }

    /**
     * 处理start运行。
     */
    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, List<Map<String, Object>> sources) {
        return startRun(agent, userId, conversationId, task, null, null, sources, null);
    }

    /**
     * 处理start运行。
     */
    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, List<Map<String, Object>> sources, Consumer<String> registerCallback) {
        return startRun(agent, userId, conversationId, task, null, null, sources, registerCallback);
    }

    /**
     * 使用外部幂等键创建 Deep 运行；幂等键必须在首次插入前写入，避免并发重复派发。
     */
    @Transactional(rollbackFor = Exception.class)
    public String startBusinessRun(AgentDefinition agent, String userId, String conversationId,
                                   String task, String externalRunId, Consumer<String> registerCallback) {
        return startRunInternal(agent, userId, conversationId, task, null, null,
                null, null, registerCallback, externalRunId);
    }

    /**
     * 使用调用方已解析的 Skill 上下文创建 Deep 运行，避免在此处重新扩大工具范围。
     */
    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, String attachmentContent, String attachments,
                           List<Map<String, Object>> sources, SkillRuntimeContext skillContext,
                           Consumer<String> registerCallback) {
        return startRunInternal(agent, userId, conversationId, task, attachmentContent, attachments, sources, skillContext,
                registerCallback, null);
    }

    /** 使用调用方本次请求提供的临时邮件凭据启动运行。 */
    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, String attachmentContent, String attachments,
                           List<Map<String, Object>> sources, SkillRuntimeContext skillContext,
                           Consumer<String> registerCallback, Map<String, Map<String, String>> runtimeSecrets) {
        String runId = startRunInternal(agent, userId, conversationId, task, attachmentContent, attachments, sources, skillContext,
                registerCallback, null);
        // startRunInternal 已完成同步派发；此重载只用于兼容入口，秘密须在真正派发前注册。
        // 当前聊天入口通过 registerRuntimeSecretsBeforeDispatch 预注册，不使用该重载。
        return runId;
    }

    /**
     * 处理start运行。
     */
    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, String attachmentContent, String attachments,
                           List<Map<String, Object>> sources, Consumer<String> registerCallback) {
        return startRunInternal(agent, userId, conversationId, task, attachmentContent, attachments, sources, null,
                registerCallback, null);
    }

    /**
     * 解析系统提示：Skill 已装配时复用其提示（已含能力索引），否则在 Agent 基础提示后追加能力索引。
     */
    private String resolveSystemPrompt(AgentDefinition agent, SkillRuntimeContext skillContext) {
        String base = skillContext == null ? (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "") : skillContext.getSystemPrompt();
        if (StringUtils.isBlank(base)) return "";
        if (skillContext == null && capabilityIndexService != null) {
            return base + capabilityIndexService.buildIndex(agent.getId(), null);
        }
        return base;
    }

    /**
     * 处理start运行Internal。
     */
    private String startRunInternal(AgentDefinition agent, String userId, String conversationId,
                                    String task, String attachmentContent, String attachments,
                                    List<Map<String, Object>> sources, SkillRuntimeContext skillContext,
                                    Consumer<String> registerCallback, String externalRunId) {
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
        List<Map<String, String>> conversationMemory = buildConversationMemory(conversationId, sessionId, userId);
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
        run.setApplicationId(agent.getApplicationId());
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
        if (StringUtils.isNotBlank(externalRunId)) {
            run.setExternalRunId(externalRunId);
        }
        agentRunService.save(run);
        String runId = run.getId();
        if (runtimeEmailCredentialStore != null) runtimeEmailCredentialStore.bindPending(runId, conversationId, userId);
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
        if (StringUtils.isBlank(externalRunId)) {
            run.setExternalRunId(runId);
        }
        run.setRetrievalSources(JSON.toJSONString(sources == null ? Collections.emptyList() : sources));
        if (!agentRunService.updateById(run)) {
            throw new IllegalStateException("保存 Deep Agent 运行元数据失败");
        }

        try {
            if (registerCallback != null) {
                registerCallback.accept(runId);
            }
            List<AgentTool> resolvedTools = skillContext == null ? toolCatalog.getBoundTools(agent.getId()) : skillContext.getTools();
            List<AgentTool> routedTools = toolRouterService.route(resolvedTools,
                    skillContext == null ? java.util.Collections.<String>emptySet() : skillContext.getRequiredToolIds(), task);
            List<String> allowedTools = routedTools.stream()
                    .filter(t -> t.getMcpToolName() != null)
                    .filter(t -> !"send_email".equals(t.getMcpToolName()) || Boolean.TRUE.equals(agent.getSmtpEnabled()))
                    .map(AgentTool::getMcpToolName)
                    .collect(Collectors.toList());
            // 文件生成由已绑定的通用 generate_artifact 工具授权；Skill 仅影响本轮提示词规范，
            // 不再通过委派令牌选择脚本或模板。
            String delegationToken = delegationTokenService.create(runId, userId, agent.getId(), allowedTools);

            Map<String, String> emailCredentialTokens = createEmailCredentialTokens(runId, userId, agent, allowedTools);

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
            request.put("system_prompt", resolveSystemPrompt(agent, skillContext));
            request.put("knowledge_sources", knowledgeSources);
            request.put("allowed_tools", allowedTools);
            request.put("tool_approval_policy", readToolApprovalPolicy(run.getSkillSnapshot()));
            request.put("delegation_token", delegationToken);
            if (!emailCredentialTokens.isEmpty()) request.put("email_credential_tokens", emailCredentialTokens);
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

            recordDeepPreliminary(run, agent, request, routedTools);
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

    /**
     * 构建会话Memory。
     */
    private List<Map<String, String>> buildConversationMemory(String conversationId, String sessionId, String userId) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        if (conversationContextService != null && StringUtils.isNotBlank(conversationId)) {
            List<ModelChatMessage> messages = conversationContextService.buildSharedConversationMemory(conversationId, sessionId);
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
        // Preferences are refreshed for every dispatch. Keep them behind stable history so they
        // cannot invalidate the provider's reusable prompt prefix.
        addConfirmedPreferences(result, userId);
        return result;
    }

    /**
     * 新增ConfirmedPreferences。
     */
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

    /**
     * 处理snapshotWithToolApprovalPolicy。
     */
    private String snapshotWithToolApprovalPolicy(SkillRuntimeContext skillContext, AgentConversation conversation) {
        JSONObject snapshot = skillContext == null || StringUtils.isBlank(skillContext.getSnapshot())
                ? new JSONObject() : JSON.parseObject(skillContext.getSnapshot());
        snapshot.put("toolApprovalPolicy", normalizeToolApprovalPolicy(conversation == null ? null : conversation.getToolApprovalPolicy()));
        return snapshot.toJSONString();
    }

    /**
     * Keep the actual Deep Agent payload inspectable, but never persist its delegation credential.
     */
    private String deepAgentInputSnapshot(Map<String, Object> request) {
        Map<String, Object> snapshot = new LinkedHashMap<>(request);
        snapshot.remove("delegation_token");
        snapshot.remove("email_credential_tokens");
        return JSON.toJSONString(snapshot);
    }

    private Map<String, String> createEmailCredentialTokens(String runId, String userId, AgentDefinition agent, List<String> allowedTools) {
        if (emailCredentialTokenService == null || !Boolean.TRUE.equals(agent.getSmtpEnabled()) || !allowedTools.contains("send_email")) return Collections.emptyMap();
        Map<String, String> tokens = new LinkedHashMap<>();
        Map<String, String> credential = runtimeEmailCredentialStore == null ? null
                : runtimeEmailCredentialStore.get(runId, userId, "user-default");
        if (credential != null) {
            credential = new LinkedHashMap<>(credential);
        }
        if (hasEmailConfiguration(agent)) {
            if (credential == null) credential = new LinkedHashMap<>();
            credential.put("sender_email", agent.getSmtpSenderEmail());
            credential.put("smtp_authorization_code", AesUtil.decrypt(agent.getSmtpAuthorizationCode()));
            credential.put("smtp_host", agent.getSmtpHost());
            credential.put("smtp_port", String.valueOf(agent.getSmtpPort()));
            credential.put("security", agent.getSmtpSecurity());
        }
        if (userService != null) {
            User user = userService.getById(userId);
            if (!hasEmailCredential(credential) && user != null && StringUtils.isNoneBlank(user.getEmail(), user.getSmtpAuthorizationCode(), user.getSmtpHost(), user.getSmtpSecurity())
                    && user.getSmtpPort() != null) {
                if (credential == null) credential = new LinkedHashMap<>();
                credential.putIfAbsent("sender_email", user.getEmail());
                credential.putIfAbsent("smtp_authorization_code", AesUtil.decrypt(user.getSmtpAuthorizationCode()));
                credential.put("smtp_host", user.getSmtpHost());
                credential.put("smtp_port", String.valueOf(user.getSmtpPort()));
                credential.put("security", user.getSmtpSecurity());
            }
        }
        if (credential != null) {
            tokens.put("user-default", emailCredentialTokenService.create(runId, userId, "user-default", credential));
        }
        return tokens;
    }

    private boolean hasEmailConfiguration(AgentDefinition agent) {
        return agent != null && StringUtils.isNoneBlank(agent.getSmtpSenderEmail(), agent.getSmtpAuthorizationCode(),
                agent.getSmtpHost(), agent.getSmtpSecurity()) && agent.getSmtpPort() != null;
    }

    private boolean hasEmailCredential(Map<String, String> credential) {
        return credential != null && StringUtils.isNoneBlank(credential.get("sender_email"), credential.get("smtp_authorization_code"),
                credential.get("smtp_host"), credential.get("smtp_port"), credential.get("security"));
    }

    /**
     * 处理readToolApprovalPolicy。
     */
    private String readToolApprovalPolicy(String snapshotJson) {
        try {
            return normalizeToolApprovalPolicy(JSON.parseObject(snapshotJson).getString("toolApprovalPolicy"));
        } catch (Exception ignored) {
            return "ask";
        }
    }

    /**
     * 规范化ToolApprovalPolicy。
     */
    private String normalizeToolApprovalPolicy(String policy) {
        return "risky".equals(policy) || "never".equals(policy) ? policy : "ask";
    }

    /**
     * 处理回调。
     */
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

    /**
     * 将 Deep 服务的原生工具中断转换成与普通 Agent 一致的确认交互卡片。
     */
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

    /**
     * 处理containsHighRiskAction。
     */
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

    /**
     * 审批通过全部。
     */
    private List<Map<String, String>> approveAll(int count) {
        List<Map<String, String>> decisions = new ArrayList<>();
        for (int i = 0; i < count; i++) decisions.add(Collections.singletonMap("type", "approve"));
        return decisions;
    }

    /**
     * 处理resumeDeepToolDecisions。
     */
    private void resumeDeepToolDecisions(String runId, List<Map<String, String>> decisions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId);
        payload.put("decisions", decisions);
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
        if (response.getStatusCode() != HttpStatus.ACCEPTED)
            throw new IllegalStateException("Deep Agent 自动批准恢复失败");
    }

    /**
     * 创建Ask用户Question。
     */
    public AgentMessage createAskUserQuestion(String runId, String dataJson) {
        AgentRun run = getDeepRun(runId);
        JSONObject data = JSON.parseObject(dataJson);
        JSONArray questions = data.getJSONArray("questions");
        if (questions == null || questions.isEmpty())
            throw new IllegalArgumentException("Deep ask_user has no questions");
        JSONObject config = new JSONObject();
        config.put("type", "group");
        config.put("layout", "tabs");
        config.put("question", StringUtils.defaultIfBlank(data.getString("question"), "请回答以下问题后继续"));
        config.put("approvalType", "deep_ask_user");
        config.put("runId", runId);
        config.put("questions", questions);
        AgentMessage message = new AgentMessage();
        message.setConversationId(run.getConversationId());
        message.setRole("assistant");
        message.setMessageType("interaction");
        message.setInteractionType("group");
        message.setInteractionStatus("pending");
        message.setContent(config.getString("question"));
        message.setQuestionConfig(config.toJSONString());
        if (!agentMessageService.save(message)) throw new IllegalStateException("保存 Deep 提问消息失败");
        updateTaskStatus(run, "WAITING_USER", "等待用户补充信息");
        return message;
    }

    /**
     * 将 Deep 服务的计划确认请求转换成交互卡片，等待用户批准后再执行。
     */
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

    /**
     * 验证会话归属，保存确认结果并让 Deep 服务恢复同一运行。
     */
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
            Object rawAnswers = answer == null ? null : answer.get("answers");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("run_id", runId);
            String planFeedback = rawAnswers instanceof Map
                    ? stringValue(((Map<?, ?>) rawAnswers).get("plan_feedback")) : null;
            if (StringUtils.isNotBlank(planFeedback)) {
                // 用户反馈方案：转发反馈让 Deep 服务按反馈重规划并重新提交审批，任务保持 WAITING_APPROVAL。
                payload.put("plan_approved", false);
                payload.put("plan_feedback", planFeedback);
            } else {
                payload.put("plan_approved", true);
                // 步骤多选框随答案整体转发（与 deep_ask_user 同约定），由 Deep 服务自行解释所选步骤。
                payload.put("answers", rawAnswers == null ? Collections.emptyMap() : rawAnswers);
            }
            ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
            if (response.getStatusCode() != HttpStatus.ACCEPTED)
                throw new IllegalStateException("Deep Agent 计划确认恢复失败");
            if (StringUtils.isBlank(planFeedback)) {
                updateTaskStatus(run, "RUNNING", null);
            }
            return runId;
        }
        if ("deep_ask_user".equals(approvalType)) {
            AgentMessage update = new AgentMessage();
            update.setId(messageId);
            update.setInteractionStatus("answered");
            update.setAnsweredAt(System.currentTimeMillis());
            agentMessageService.updateById(update);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("run_id", runId);
            payload.put("answers", answer == null ? Collections.emptyMap() : answer.get("answers"));
            ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", payload);
            if (response.getStatusCode() != HttpStatus.ACCEPTED)
                throw new IllegalStateException("Deep Agent 提问恢复失败");
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

    /**
     * 处理pause。
     */
    public void pause(String runId, String userId) {
        AgentRun run = getDeepRun(runId);
        if (!userId.equals(run.getUserId()))
            throw new IllegalArgumentException("Deep run does not belong to the current user");
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/pause", Collections.emptyMap());
        if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 暂停请求失败");
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus(6);
        agentRunService.updateById(update);
        updateTaskStatus(run, "PAUSED", "用户暂停");
    }

    /**
     * Deep Runtime 重启或连接中断时的回调投影，不再向外部重复发送暂停请求。
     */
    public boolean markPausedFromCallback(String runId, String reason) {
        AgentRun run = getDeepRun(runId);
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, 6)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (updated) updateTaskStatus(run, "PAUSED", reason);
        return updated;
    }

    /**
     * 处理resume。
     */
    public void resume(String runId, String userId) {
        AgentRun run = getDeepRun(runId);
        if (!userId.equals(run.getUserId()))
            throw new IllegalArgumentException("Deep run does not belong to the current user");
        if (!Integer.valueOf(6).equals(run.getStatus())) throw new IllegalArgumentException("Deep run is not paused");
        ResponseEntity<String> response = signingClient.signedPost("/v1/runs/" + runId + "/resume", Collections.emptyMap());
        if (response.getStatusCode() != HttpStatus.ACCEPTED) throw new IllegalStateException("Deep Agent 继续请求失败");
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus(3);
        agentRunService.updateById(update);
        updateTaskStatus(run, "RUNNING", null);
    }

    /**
     * Re-check live binding and the immutable run scope before resuming a paused external tool call.
     */
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

    /**
     * 查找BoundTool。
     */
    private AgentTool findBoundTool(String agentId, String toolName) {
        if (StringUtils.isBlank(toolName)) {
            return null;
        }
        return toolCatalog.getBoundTools(agentId).stream()
                .filter(tool -> toolName.equals(tool.getMcpToolName()) || toolName.equals(tool.getName()))
                .findFirst().orElse(null);
    }

    /**
     * 构建任务Context。
     */
    private String buildTaskContext(String task, String attachmentContent) {
        if (StringUtils.isBlank(attachmentContent)) {
            return task;
        }
        return StringUtils.defaultString(task) + "\n\n附件内容：\n" + attachmentContent;
    }

    /**
     * 兼容旧调用：未携带耗时信息时由 12 参实现按步骤时间差估算。
     */
    public CompletedRun completeRun(String runId, String content, String model,
                                    Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                    String reasoningContent, Integer reasoningTokens, String toolCalls, String citations) {
        return completeRun(runId, content, model, promptTokens, completionTokens, totalTokens,
                reasoningContent, reasoningTokens, toolCalls, citations, null, null);
    }

    /**
     * Deep Agent 完成回调：持久化最终回答与用量，并把请求耗时写入消息与运行审计。
     * latencyMs 优先取完成事件载荷（latency_ms/latencyMs）；缺失时用已持久化的
     * run.started 与 run.completed 步骤 occurred_at 差值兜底，保证审计不缺耗时。
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletedRun completeRun(String runId, String content, String model,
                                    Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                    String reasoningContent, Integer reasoningTokens, String toolCalls, String citations,
                                    Integer latencyMs, Long completedOccurredAt) {
        AgentRun run = getDeepRun(runId);
        boolean claimed = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_SUCCEEDED)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (!claimed) {
            return null;
        }
        Integer effectiveLatencyMs = resolveLatencyMs(runId, latencyMs, completedOccurredAt);
        // 拆分人工等待：审计里的 latencyMs 是含审批/提问等待的 wall-clock，
        // waitingMs 单独记录等待时长，执行耗时 = latencyMs - waitingMs。
        Long waitingMs = computeWaitingMs(run.getConversationId());
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
        message.setLatencyMs(effectiveLatencyMs);
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
                .set(AgentRun::getLatencyMs, effectiveLatencyMs)
                .set(AgentRun::getWaitingMs, waitingMs)
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
        recordDeepFinalMetric(runId, promptTokens);
        if (agentSessionMemoryService != null) {
            agentSessionMemoryService.recordTaskConclusion(run.getSessionId(), run.getTaskId(), runId, content);
        }
        updateTaskStatus(run, "COMPLETED", null);
        return new CompletedRun(run.getConversationId(), message.getId(), message.getCreatedAt(), effectiveLatencyMs);
    }

    /**
     * 载荷缺失时按已落库的 run.started / run.completed 步骤时间差估算请求耗时。
     */
    private Integer resolveLatencyMs(String runId, Integer payloadLatencyMs, Long completedOccurredAt) {
        if (payloadLatencyMs != null && payloadLatencyMs >= 0) {
            return payloadLatencyMs;
        }
        if (completedOccurredAt == null || completedOccurredAt <= 0) {
            return null;
        }
        AgentRunStep started = agentRunStepService.getOne(Wrappers.lambdaQuery(AgentRunStep.class)
                .eq(AgentRunStep::getRunId, runId)
                .eq(AgentRunStep::getEventType, "run.started")
                .eq(AgentRunStep::getDeleted, false)
                .last("LIMIT 1"), false);
        if (started == null || started.getOccurredAt() == null || started.getOccurredAt() <= 0) {
            return null;
        }
        long diff = completedOccurredAt - started.getOccurredAt();
        return diff >= 0 && diff <= Integer.MAX_VALUE ? (int) diff : null;
    }

    /**
     * 汇总会话内已答复的交互消息(计划/工具审批、提问)的人工等待时长(毫秒)。
     */
    private Long computeWaitingMs(String conversationId) {
        if (agentMessageService == null || StringUtils.isBlank(conversationId)) {
            return 0L;
        }
        List<AgentMessage> interactions = agentMessageService.list(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getMessageType, "interaction")
                .eq(AgentMessage::getInteractionStatus, "answered")
                .eq(AgentMessage::getDeleted, false));
        if (interactions == null || interactions.isEmpty()) {
            return 0L;
        }
        long waiting = 0L;
        for (AgentMessage message : interactions) {
            if (message.getCreatedAt() != null && message.getAnsweredAt() != null
                    && message.getAnsweredAt() >= message.getCreatedAt()) {
                waiting += message.getAnsweredAt() - message.getCreatedAt();
            }
        }
        return waiting;
    }

    /**
     * 处理markRunning。
     */
    public boolean markRunning(String runId) {
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_RUNNING)
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getStatus, STATUS_QUEUED));
        if (updated) updateTaskStatus(getDeepRun(runId), "RUNNING", null);
        return updated;
    }

    /**
     * 处理markSucceeded。
     */
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

    /**
     * 处理markFailed。
     */
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

    /**
     * 处理markCancelled。
     */
    public boolean markCancelled(String runId) {
        AgentRun run = getDeepRun(runId);
        boolean updated = agentRunService.update(null, Wrappers.lambdaUpdate(AgentRun.class)
                .set(AgentRun::getStatus, STATUS_CANCELLED)
                .eq(AgentRun::getId, runId)
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING));
        if (updated) updateTaskStatus(run, "CANCELLED", "用户取消");
        return updated;
    }

    /**
     * 更新任务状态。
     */
    private void updateTaskStatus(AgentRun run, String status, String reason) {
        if (run != null && agentTaskService != null) {
            agentTaskService.updateStatus(run.getTaskId(), status, run.getId(), reason);
            if (agentSessionService != null)
                agentSessionService.updateTaskState(run.getSessionId(), run.getTaskId(), status);
            if (agentTaskEventService != null)
                agentTaskEventService.record(run.getTaskId(), run.getId(), "task.status_changed", status + (StringUtils.isBlank(reason) ? "" : "：" + reason));
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                dispatchNextQueuedAfterCommit(run.getSessionId());
            }
        }
    }

    /**
     * Start exactly one queued task after the active task has durably released the Session.
     */
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
                AgentDefinition metricAgent = new AgentDefinition();
                metricAgent.setId(nextRun.getAgentDefinitionId());
                metricAgent.setModel(nextRun.getModel());
                recordDeepPreliminary(nextRun, metricAgent, request, Collections.<AgentTool>emptyList());
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
                /**
                 * 处理afterCommit。
                 */
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    /**
     * 获取Deep运行用于Reconciliation。
     */
    public AgentRun getDeepRunForReconciliation(String runId) {
        return getDeepRun(runId);
    }

    /**
     * 构建知识库Sources。
     */
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
     * 记录 Deep Agent 派发给外部运行时的上下文估算指标。
     */
    @SuppressWarnings("unchecked")
    private void recordDeepPreliminary(AgentRun run, AgentDefinition agent, Map<String, Object> request,
                                       List<AgentTool> tools) {
        if (contextMetricService == null || run == null || request == null) {
            return;
        }
        List<ModelChatMessage> messages = new ArrayList<ModelChatMessage>();
        String systemPrompt = objectString(request.get("system_prompt"));
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new ModelChatMessage("system", systemPrompt));
        }
        Object memoryObject = request.get("conversation_memory");
        if (memoryObject instanceof List) {
            for (Object entryObject : (List<?>) memoryObject) {
                if (!(entryObject instanceof Map)) continue;
                Map<?, ?> entry = (Map<?, ?>) entryObject;
                String role = StringUtils.defaultIfBlank(objectString(entry.get("role")), "system");
                String content = objectString(entry.get("content"));
                if (StringUtils.isNotBlank(content)) {
                    messages.add(new ModelChatMessage(role, content));
                }
            }
        }
        Object taskState = request.get("task_state");
        if (taskState != null) {
            messages.add(new ModelChatMessage("system", "【当前Deep任务】" + JSON.toJSONString(taskState)));
        }
        Object knowledgeSources = request.get("knowledge_sources");
        if (knowledgeSources instanceof List && !((List<?>) knowledgeSources).isEmpty()) {
            messages.add(new ModelChatMessage("system", "【运行时上下文】" + JSON.toJSONString(knowledgeSources)));
        }
        String task = objectString(request.get("task"));
        if (StringUtils.isNotBlank(task)) {
            messages.add(new ModelChatMessage("user", task));
        }
        contextMetricService.recordPreliminary(run.getId(),
                run.getAttemptNo() == null ? 1 : run.getAttemptNo(),
                "DEEP_STEP", "NOT_NEEDED", messages, tools, agent, null);
    }

    /**
     * 空值安全的字符串转换。
     */
    private String objectString(Object value) {
        return value == null ? "" : StringUtils.trimToEmpty(String.valueOf(value));
    }

    /**
     * Deep 完成回调返回 provider 用量后，写入对应最终指标。
     */
    private void recordDeepFinalMetric(String runId, Integer promptTokens) {
        if (contextMetricService == null) {
            return;
        }
        contextMetricService.recordFinalForLatestPreliminary(runId, "DEEP_STEP", promptTokens, "NOT_NEEDED");
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

    /**
     * 延续同一任务时注入的身份提示与最新计划摘要，帮助 Deep Agent 沿用任务目标而非重新规划。
     */
    private Map<String, String> continuationContextHint(AgentTask taskRecord) {
        StringBuilder content = new StringBuilder("【当前任务继续】你正在继续会话中的任务「")
                .append(StringUtils.defaultString(taskRecord.getTitle(), "当前任务"))
                .append("」。请结合该任务已完成的步骤与已有上下文继续推进；如需调整目标，请依据用户最新指示修订计划，不要当作全新任务重新开始。");
        String planSummary = latestPlanSummary(taskRecord == null ? null : taskRecord.getId());
        if (StringUtils.isNotBlank(planSummary)) {
            content.append("\n\n【任务当前计划】\n").append(planSummary);
        }
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("role", "system");
        item.put("content", content.toString());
        return item;
    }

    /**
     * 从持久化的计划投影取最新版本步骤作为延续上下文，使 Deep Agent 在无法从
     * 检查点恢复时仍能沿用已批准/已执行的计划，避免当作全新任务重新开始。
     */
    private String latestPlanSummary(String taskId) {
        if (planService == null || StringUtils.isBlank(taskId)) {
            return "";
        }
        try {
            AgentRunPlanVo plan = planService.detailByTaskId(taskId);
            if (plan == null || plan.getVersions() == null || plan.getVersions().isEmpty()) {
                return "";
            }
            AgentRunPlanVo.Version version = plan.getVersions().get(plan.getVersions().size() - 1);
            if (version == null || version.getSteps() == null || version.getSteps().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            int index = 1;
            for (AgentRunPlanVo.Step step : version.getSteps()) {
                String status = StringUtils.defaultIfBlank(step.getStatus(), "PENDING");
                String marker = "COMPLETED".equals(status) ? "[完成]"
                        : ("RUNNING".equals(status) ? "[进行中]" : "[待办]");
                sb.append(index++).append(". ").append(marker).append(" ")
                        .append(StringUtils.defaultString(step.getTitle())).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("读取任务计划摘要失败: taskId={}", taskId, e);
            return "";
        }
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
        String normalized = normalizeRouteInput(input);
        // 目标变更信号：覆盖更多自然措辞，减少"换种方式/换个思路/推翻重来"等被误判为继续。
        if (containsAny(normalized,
                "改为", "改成", "换成", "换种", "换个", "换一个", "换成做",
                "调整为", "调整成", "调整一下",
                "不再", "取消原", "取消之前", "取消当前", "变更目标",
                "重新", "重做", "重来", "推翻", "换个方向", "换个思路")) {
            return TaskRoute.goalChanged(active);
        }
        // 会话内只有一个任务：其余消息都视为对其计划的继续/补充（含暂停后继续）。
        return TaskRoute.continueTask(active);
    }

    /**
     * 规范化路由输入：去除首尾空白并折叠连续空白，避免换行/多空格影响关键词匹配。
     */
    private String normalizeRouteInput(String input) {
        String trimmed = StringUtils.defaultString(input).trim().toLowerCase(Locale.ROOT);
        return trimmed.replaceAll("\\s+", " ");
    }

    /**
     * 处理containsAny。
     */
    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    /**
     * 下一个AttemptNo。
     */
    private int nextAttemptNo(String taskId) {
        if (StringUtils.isBlank(taskId)) return 1;
        AgentRun latestRun = agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getTaskId, taskId).eq(AgentRun::getExecutionMode, "DEEP")
                .eq(AgentRun::getDeleted, false).orderByDesc(AgentRun::getAttemptNo).last("limit 1"), false);
        return latestRun == null || latestRun.getAttemptNo() == null ? 1 : latestRun.getAttemptNo() + 1;
    }

    /**
     * 表示任务Route。
     */
    private static final class TaskRoute {
        private static final TaskRoute NEW_TASK = new TaskRoute("NEW_TASK", null, false, "创建新的会话任务");
        private final String type;
        private final AgentTask activeTask;
        private final boolean reuseTask;
        private final String summary;

        /**
         * 创建 {@code TaskRoute} 实例。
         */
        private TaskRoute(String type, AgentTask activeTask, boolean reuseTask, String summary) {
            this.type = type;
            this.activeTask = activeTask;
            this.reuseTask = reuseTask;
            this.summary = summary;
        }

        /**
         * 处理continue任务。
         */
        private static TaskRoute continueTask(AgentTask task) {
            return new TaskRoute("CONTINUE", task, true, "识别为对暂停任务的明确补充或继续");
        }

        /**
         * 处理goalChanged。
         */
        private static TaskRoute goalChanged(AgentTask task) {
            return new TaskRoute("GOAL_CHANGED", task, true, "识别为对暂停任务的目标变更");
        }

        /**
         * 处理name。
         */
        private String name() {
            return type;
        }

        /**
         * 事件Summary。
         */
        private String eventSummary() {
            return summary;
        }
    }

    /**
     * 处理truncate。
     */
    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Deep runs are submitted after the frontend creates an untitled conversation.
     */
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

    /**
     * 处理stringValue。
     */
    private String stringValue(Object obj) {
        return obj == null ? null : obj.toString();
    }

    /**
     * 处理record知识库OutcomeAfterCommit。
     */
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
                /**
                 * 处理afterCommit。
                 */
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    /**
     * 处理deserializeSources。
     */
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

    /**
     * 获取Deep运行。
     */
    private AgentRun getDeepRun(String runId) {
        AgentRun run = agentRunService.getById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !"DEEP".equals(run.getExecutionMode())) {
            throw new IllegalArgumentException("Unknown Deep Agent run: " + runId);
        }
        return run;
    }

    /**
     * 表示Completed运行。
     */
    public static class CompletedRun {
        private final String conversationId;
        private final String messageId;
        /**
         * 完成消息的创建时间，即请求时间；供 done 事件与前端审计展示。
         */
        private final Long requestTime;
        /**
         * 实际落库的请求耗时（含步骤时间差兜底结果）；供 done 事件展示。
         */
        private final Integer latencyMs;

        /**
         * 创建 {@code CompletedRun} 实例。
         */
        public CompletedRun(String conversationId, String messageId) {
            this(conversationId, messageId, null, null);
        }

        /**
         * 创建 {@code CompletedRun} 实例。
         */
        public CompletedRun(String conversationId, String messageId, Long requestTime) {
            this(conversationId, messageId, requestTime, null);
        }

        /**
         * 创建 {@code CompletedRun} 实例。
         */
        public CompletedRun(String conversationId, String messageId, Long requestTime, Integer latencyMs) {
            this.conversationId = conversationId;
            this.messageId = messageId;
            this.requestTime = requestTime;
            this.latencyMs = latencyMs;
        }

        /**
         * 获取会话Id。
         */
        public String getConversationId() {
            return conversationId;
        }

        /**
         * 获取消息Id。
         */
        public String getMessageId() {
            return messageId;
        }

        /**
         * 获取RequestTime。
         */
        public Long getRequestTime() {
            return requestTime;
        }

        /**
         * 获取LatencyMs。
         */
        public Integer getLatencyMs() {
            return latencyMs;
        }
    }
}
