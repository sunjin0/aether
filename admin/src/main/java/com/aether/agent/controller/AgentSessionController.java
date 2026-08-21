package com.aether.agent.controller;

import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.AgentTask;
import com.aether.agent.entity.AgentTaskEvent;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentTaskEventService;
import com.aether.agent.service.AgentTaskService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.skill.service.SkillContextService;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.model.ModelChatMessage;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 持续 Deep Agent Session 与 Task 查询接口。
 */
@RestController
@Permission(path = "/agent/chat")
@RequestMapping("/api/agent/session")
public class AgentSessionController {
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final String IDEMPOTENCY_PROCESSING = "__PROCESSING__";

    private final AgentSessionService sessions;
    private final AgentTaskService tasks;
    private final AgentRunPlanService plans;
    private final AgentTaskEventService taskEvents;
    private final AgentSessionMemoryService sessionMemories;
    private final AdminPreferenceService preferences;
    private final DeepAgentRunService deepAgentRuns;
    private final AgentDefinitionService agentDefinitions;
    private final AgentConversationService conversations;
    private final KnowledgeContextService knowledgeContext;
    private final SkillContextService skillContextService;
    @Autowired(required = false)
    @Qualifier("objectRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 {@code AgentSessionController} 实例。
     */
    public AgentSessionController(AgentSessionService sessions, AgentTaskService tasks, AgentRunPlanService plans,
                                  AgentTaskEventService taskEvents, AgentSessionMemoryService sessionMemories,
                                  AdminPreferenceService preferences, DeepAgentRunService deepAgentRuns,
                                  AgentDefinitionService agentDefinitions, AgentConversationService conversations,
                                  KnowledgeContextService knowledgeContext, SkillContextService skillContextService) {
        this.sessions = sessions;
        this.tasks = tasks;
        this.plans = plans;
        this.taskEvents = taskEvents;
        this.sessionMemories = sessionMemories;
        this.preferences = preferences;
        this.deepAgentRuns = deepAgentRuns;
        this.agentDefinitions = agentDefinitions;
        this.conversations = conversations;
        this.knowledgeContext = knowledgeContext;
        this.skillContextService = skillContextService;
    }

    /**
     * 详情当前请求。
     */
    @GetMapping("/conversation/{conversationId}")
    public WebResponse<Map<String, Object>> detail(@PathVariable String conversationId) {
        String userId = currentUserId();
        AgentSession session = sessions.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversationId)
                .eq(AgentSession::getUserId, userId)
                .eq(AgentSession::getDeleted, false));
        if (session == null) throw new ServerException(404, "Agent Session 不存在");
        List<AgentTask> sessionTasks = tasks.list(Wrappers.lambdaQuery(AgentTask.class)
                .eq(AgentTask::getSessionId, session.getId()).eq(AgentTask::getDeleted, false)
                .orderByDesc(AgentTask::getCreatedAt));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("session", session);
        result.put("tasks", sessionTasks);
        return WebResponse.OK(result);
    }

    /**
     * 当前用户范围的持续 Agent 运营概览，不返回其他用户或原始推理数据。
     */
    @GetMapping("/overview")
    public WebResponse<Map<String, Object>> overview() {
        String userId = currentUserId();
        List<AgentSession> ownedSessions = sessions.list(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getUserId, userId).eq(AgentSession::getDeleted, false));
        List<AgentTask> ownedTasks = tasks.list(Wrappers.lambdaQuery(AgentTask.class)
                .eq(AgentTask::getUserId, userId).eq(AgentTask::getDeleted, false));
        Map<String, Integer> taskStatusCounts = new LinkedHashMap<String, Integer>();
        int completed = 0;
        int manualIntervention = 0;
        for (AgentTask item : ownedTasks) {
            String status = StringUtils.defaultIfBlank(item.getStatus(), "UNKNOWN");
            taskStatusCounts.put(status, taskStatusCounts.getOrDefault(status, 0) + 1);
            if ("COMPLETED".equals(status)) completed++;
            if ("WAITING_USER".equals(status) || "WAITING_APPROVAL".equals(status) || "PAUSED".equals(status))
                manualIntervention++;
        }
        int activeSessions = 0;
        long lastActiveAt = 0L;
        List<String> sessionIds = new ArrayList<String>();
        for (AgentSession session : ownedSessions) {
            sessionIds.add(session.getId());
            if (!"ARCHIVED".equals(session.getStatus())) activeSessions++;
            if (session.getLastActiveAt() != null) lastActiveAt = Math.max(lastActiveAt, session.getLastActiveAt());
        }
        long memoryCount = sessionIds.isEmpty() ? 0 : sessionMemories.count(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .in(AgentSessionMemory::getSessionId, sessionIds).eq(AgentSessionMemory::getDeleted, false));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sessionCount", ownedSessions.size());
        result.put("activeSessionCount", activeSessions);
        result.put("taskCount", ownedTasks.size());
        result.put("completedTaskCount", completed);
        result.put("taskCompletionRate", ownedTasks.isEmpty() ? 0D : (double) completed / ownedTasks.size());
        result.put("manualInterventionTaskCount", manualIntervention);
        result.put("taskStatusCounts", taskStatusCounts);
        result.put("memoryCount", memoryCount);
        result.put("lastActiveAt", lastActiveAt == 0L ? null : lastActiveAt);
        return WebResponse.OK(result);
    }

    /**
     * 在现有持续 Session 中投递一个新的 Deep 任务。Skill、检索和运行快照均由服务端装配，
     * 客户端不能扩大工具范围或伪造用户身份。
     */
    @PostMapping("/conversation/{conversationId}/messages")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<Map<String, String>> submitMessage(@PathVariable String conversationId,
                                                          @RequestBody AgentChatDto input) {
        String userId = currentUserId();
        AgentSession session = sessions.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversationId).eq(AgentSession::getUserId, userId)
                .eq(AgentSession::getDeleted, false));
        if (session == null) throw new ServerException(404, "Agent Session 不存在");
        String message = StringUtils.trimToEmpty(input.getMessage());
        if (StringUtils.isBlank(message)) throw new ServerException(400, "任务内容不能为空");
        AgentConversation conversation = conversations.getById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted()) || !userId.equals(conversation.getUserId())
                || !Integer.valueOf(0).equals(conversation.getStatus())) {
            throw new ServerException(409, "聊天会话不可继续执行");
        }
        AgentDefinition agent = agentDefinitions.getById(session.getAgentDefinitionId());
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted()) || !"DEEP".equals(agent.getExecutionMode())) {
            throw new ServerException(422, "会话未绑定可用的 Deep Agent");
        }
        input.setAgentId(agent.getId());
        input.setConversationId(conversationId);
        SkillRuntimeContext skillContext = skillContextService.resolve(agent, input, message, null);
        // knowledgeContext.enhance() 会向 context 追加运行时上下文，必须传入可变列表；
        // emptyList/singletonList 会在追加时抛 UnsupportedOperationException。
        List<ModelChatMessage> systemContext = new ArrayList<ModelChatMessage>();
        if (StringUtils.isNotBlank(skillContext.getSystemPrompt())) {
            systemContext.add(new ModelChatMessage("system", skillContext.getSystemPrompt()));
        }
        List<Map<String, Object>> sources = knowledgeContext.enhance(systemContext, userId, conversationId,
                agent.getId(), message, skillContext.getKnowledgeBaseIds(), input.getRetrievalMode());
        String runId = deepAgentRuns.startRun(agent, userId, conversationId, message, null, null, sources, skillContext, null);
        AgentTask task = tasks.getOne(Wrappers.lambdaQuery(AgentTask.class).eq(AgentTask::getCurrentRunId, runId)
                .eq(AgentTask::getUserId, userId).eq(AgentTask::getDeleted, false));
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("sessionId", session.getId());
        result.put("taskId", task == null ? null : task.getId());
        result.put("runId", runId);
        return WebResponse.OK(result);
    }

    /**
     * Session 范围的可审计时间线。仅返回任务、计划/状态和交互摘要，绝不返回原始推理链。
     */
    @GetMapping("/conversation/{conversationId}/timeline")
    public WebResponse<Map<String, Object>> timeline(@PathVariable String conversationId) {
        String userId = currentUserId();
        AgentSession session = sessions.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversationId)
                .eq(AgentSession::getUserId, userId)
                .eq(AgentSession::getDeleted, false));
        if (session == null) throw new ServerException(404, "Agent Session 不存在");
        List<AgentTask> sessionTasks = tasks.list(Wrappers.lambdaQuery(AgentTask.class)
                .eq(AgentTask::getSessionId, session.getId()).eq(AgentTask::getDeleted, false)
                .orderByAsc(AgentTask::getCreatedAt));
        List<AgentTaskEvent> events = new ArrayList<AgentTaskEvent>();
        for (AgentTask item : sessionTasks) {
            events.addAll(taskEvents.listByTaskId(item.getId()));
        }
        events.sort(Comparator.comparing(AgentTaskEvent::getOccurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("session", session);
        result.put("tasks", sessionTasks);
        result.put("events", events);
        return WebResponse.OK(result);
    }

    /**
     * 任务当前请求。
     */
    @GetMapping("/task/{taskId}")
    public WebResponse<Map<String, Object>> task(@PathVariable String taskId) {
        AgentTask task = requireOwnedTask(taskId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("task", task);
        result.put("plan", plans.detailByTaskId(taskId));
        return WebResponse.OK(result);
    }

    /**
     * 处理feedback。
     */
    @PostMapping("/task/{taskId}/feedback")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<Void> feedback(@PathVariable String taskId, @RequestBody Map<String, Object> payload) {
        AgentTask task = requireOwnedTask(taskId);
        Object rawRating = payload.get("rating");
        int rating;
        try {
            rating = Integer.parseInt(String.valueOf(rawRating));
        } catch (Exception ignored) {
            throw new ServerException(400, "评分必须为 1 到 5");
        }
        if (rating < 1 || rating > 5) throw new ServerException(400, "评分必须为 1 到 5");
        Object rawNote = payload.get("note");
        String note = StringUtils.abbreviate(rawNote == null ? "" : String.valueOf(rawNote), 500);
        taskEvents.record(taskId, task.getCurrentRunId(), "task.feedback",
                "rating=" + rating + (StringUtils.isBlank(note) ? "" : "；" + note));
        return WebResponse.OK((Void) null);
    }

    /**
     * 处理events。
     */
    @GetMapping("/task/{taskId}/events")
    public WebResponse<List<AgentTaskEvent>> events(@PathVariable String taskId) {
        AgentTask task = requireOwnedTask(taskId);
        return WebResponse.OK(taskEvents.listByTaskId(task.getId()));
    }

    /**
     * 以 Task 为入口暂停，Run 接口仍保留作兼容层。
     */
    @PostMapping("/task/{taskId}/pause")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<Void> pauseTask(@PathVariable String taskId) {
        AgentTask task = requireOwnedTask(taskId);
        requireCurrentRun(task);
        deepAgentRuns.pause(task.getCurrentRunId(), currentUserId());
        plans.markPaused(task.getCurrentRunId(), "用户暂停");
        return WebResponse.OK((Void) null);
    }

    /**
     * 从当前 Task 的最新安全检查点继续执行。
     */
    @PostMapping("/task/{taskId}/resume")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<Void> resumeTask(@PathVariable String taskId) {
        AgentTask task = requireOwnedTask(taskId);
        requireCurrentRun(task);
        deepAgentRuns.resume(task.getCurrentRunId(), currentUserId());
        plans.markRunning(task.getCurrentRunId());
        return WebResponse.OK((Void) null);
    }

    /**
     * 将 ask_user 的真实选择或自定义输入恢复到当前 Task。
     * 审批与问答沿用既有消息协议，但入口和权限均以 Task 为准。
     */
    @PostMapping("/task/{taskId}/input")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<Map<String, String>> inputTask(@PathVariable String taskId,
                                                      @RequestBody Map<String, Object> payload) {
        AgentTask task = requireOwnedTask(taskId);
        if (!"WAITING_USER".equals(task.getStatus()) && !"WAITING_APPROVAL".equals(task.getStatus())) {
            throw new ServerException(409, "当前任务不等待用户输入或审批");
        }
        Object rawMessageId = payload.get("messageId");
        String messageId = rawMessageId == null ? "" : StringUtils.trimToEmpty(String.valueOf(rawMessageId));
        if (StringUtils.isBlank(messageId)) throw new ServerException(400, "缺少待恢复的交互消息 ID");
        AgentSession session = requireOwnedSession(task.getSessionId());
        String runId = deepAgentRuns.resumeToolApproval(session.getConversationId(), messageId, currentUserId(), payload);
        plans.markRunning(runId);
        taskEvents.record(taskId, runId, "task.input_received", "已接收用户输入并恢复任务");
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("runId", runId);
        return WebResponse.OK(result);
    }

    /**
     * 处理memories。
     */
    @GetMapping("/{sessionId}/memory")
    public WebResponse<List<AgentSessionMemory>> memories(@PathVariable String sessionId) {
        requireOwnedSession(sessionId);
        return WebResponse.OK(sessionMemories.listInjectable(sessionId, 100));
    }

    /**
     * Session 级运行概览，供聊天页和后续运营面板对账。
     */
    @GetMapping("/{sessionId}/metrics")
    public WebResponse<Map<String, Object>> metrics(@PathVariable String sessionId) {
        AgentSession session = requireOwnedSession(sessionId);
        List<AgentTask> sessionTasks = tasks.list(Wrappers.lambdaQuery(AgentTask.class)
                .eq(AgentTask::getSessionId, sessionId).eq(AgentTask::getDeleted, false));
        Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
        for (AgentTask task : sessionTasks) {
            String status = StringUtils.defaultIfBlank(task.getStatus(), "UNKNOWN");
            byStatus.put(status, byStatus.getOrDefault(status, 0) + 1);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sessionId", sessionId);
        result.put("activeTaskId", session.getActiveTaskId());
        result.put("lastActiveAt", session.getLastActiveAt());
        result.put("taskCount", sessionTasks.size());
        result.put("taskStatusCounts", byStatus);
        result.put("memoryCount", sessionMemories.count(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getSessionId, sessionId).eq(AgentSessionMemory::getDeleted, false)));
        return WebResponse.OK(result);
    }

    /**
     * 删除Memory。
     */
    @DeleteMapping("/{sessionId}/memory/{memoryId}")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<Void> deleteMemory(@PathVariable String sessionId,
                                          @PathVariable String memoryId,
                                          @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return withIdempotency(sessionId, "delete:" + memoryId, idempotencyKey, () -> {
            requireOwnedSession(sessionId);
            sessionMemories.deleteMemory(sessionId, memoryId, expectedVersion(ifMatch), "用户删除会话记忆");
            return WebResponse.OK((Void) null);
        });
    }

    /**
     * 用户确认、修正或删除跨任务可用的稳定偏好。
     */
    @PostMapping("/{sessionId}/memory/feedback")
    @Permission(path = "/agent/chat", type = Permission.Type.Write)
    public WebResponse<AdminPreference> memoryFeedback(@PathVariable String sessionId,
                                                       @RequestBody Map<String, Object> payload) {
        String userId = currentUserId();
        AgentSession session = sessions.getById(sessionId);
        if (session == null || Boolean.TRUE.equals(session.getDeleted()) || !userId.equals(session.getUserId())) {
            throw new ServerException(404, "Agent Session 不存在");
        }
        String category = StringUtils.trimToEmpty(String.valueOf(payload.get("category"))).toLowerCase();
        String keyName = StringUtils.trimToEmpty(String.valueOf(payload.get("keyName"))).toLowerCase();
        String value = StringUtils.trimToEmpty(String.valueOf(payload.get("value")));
        boolean confirmed = !Boolean.FALSE.equals(payload.get("confirmed"));
        if (!isPreferenceCategory(category) || StringUtils.isBlank(keyName)) {
            throw new ServerException(400, "偏好类别或键无效");
        }
        AdminPreference preference = preferences.getOne(Wrappers.lambdaQuery(AdminPreference.class)
                .eq(AdminPreference::getAdminId, userId)
                .eq(AdminPreference::getCategory, category)
                .eq(AdminPreference::getKeyName, keyName)
                .eq(AdminPreference::getScope, AdminPreference.SCOPE_GLOBAL)
                .eq(AdminPreference::getDeleted, false));
        if (!confirmed) {
            if (preference != null && AdminPreference.SOURCE_EXPLICIT.equals(preference.getSource())) {
                preferences.removeById(preference.getId());
            }
            return WebResponse.OK(null);
        }
        if (StringUtils.isBlank(value) || value.length() > 1000) {
            throw new ServerException(400, "偏好内容不能为空且长度不能超过 1000");
        }
        if (preference == null) {
            preference = new AdminPreference();
            preference.setAdminId(userId);
            preference.setCategory(category);
            preference.setKeyName(keyName);
            preference.setScope(AdminPreference.SCOPE_GLOBAL);
            preference.setPriority(80);
            preference.setUsageCount(0);
        }
        preference.setValue(value);
        preference.setSource(AdminPreference.SOURCE_EXPLICIT);
        preference.setConfidence(BigDecimal.ONE);
        preference.setStatus(AdminPreference.STATUS_ENABLED);
        if (preference.getId() == null) preferences.save(preference);
        else preferences.updateById(preference);
        return WebResponse.OK(preference);
    }

    /**
     * 处理requireOwned任务。
     */
    private AgentTask requireOwnedTask(String taskId) {
        String userId = currentUserId();
        AgentTask task = tasks.getById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted()) || !userId.equals(task.getUserId())) {
            throw new ServerException(404, "Agent Task 不存在");
        }
        return task;
    }

    /**
     * 处理require当前运行。
     */
    private void requireCurrentRun(AgentTask task) {
        if (StringUtils.isBlank(task.getCurrentRunId())) {
            throw new ServerException(409, "任务尚未分配可控制的运行实例");
        }
    }

    /**
     * 处理requireOwned会话。
     */
    private AgentSession requireOwnedSession(String sessionId) {
        String userId = currentUserId();
        AgentSession session = sessions.getById(sessionId);
        if (session == null || Boolean.TRUE.equals(session.getDeleted()) || !userId.equals(session.getUserId())) {
            throw new ServerException(404, "Agent Session 不存在");
        }
        return session;
    }

    /**
     * 当前用户Id。
     */
    private String currentUserId() {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) throw new ServerException(401, "未登录");
        return userId;
    }

    /**
     * 解析记忆版本。
     */
    private Integer expectedVersion(String ifMatch) {
        if (StringUtils.isBlank(ifMatch)) {
            throw new ServerException(400, "If-Match 不能为空");
        }
        String normalized = StringUtils.remove(StringUtils.trimToEmpty(ifMatch), '"');
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException e) {
            throw new ServerException(400, "If-Match 必须是记忆版本号");
        }
    }

    /**
     * 执行带 HTTP 幂等键的 Session 记忆写入。
     */
    @SuppressWarnings("unchecked")
    private <T> WebResponse<T> withIdempotency(String sessionId, String action,
                                               String idempotencyKey, Supplier<WebResponse<T>> operation) {
        String normalized = StringUtils.trimToEmpty(idempotencyKey);
        if (StringUtils.isBlank(normalized)) {
            throw new ServerException(400, "Idempotency-Key 不能为空");
        }
        if (normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ServerException(400, "Idempotency-Key 不能超过128个字符");
        }
        if (redisTemplate == null) {
            return operation.get();
        }
        String cacheKey = "agent:session:memory:idempotency:" + currentUserId()
                + ":" + sessionId + ":" + action + ":" + normalized;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                cacheKey, IDEMPOTENCY_PROCESSING, 24, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(acquired)) {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof WebResponse) {
                return (WebResponse<T>) cached;
            }
            throw new ServerException(409, "重复请求正在处理中，请稍后重试");
        }
        try {
            WebResponse<T> response = operation.get();
            redisTemplate.opsForValue().set(cacheKey, response, 24, TimeUnit.HOURS);
            return response;
        } catch (RuntimeException e) {
            redisTemplate.delete(cacheKey);
            throw e;
        }
    }

    /**
     * 判断是否为偏好Category。
     */
    private boolean isPreferenceCategory(String category) {
        return AdminPreference.CATEGORY_LANGUAGE.equals(category) || AdminPreference.CATEGORY_STYLE.equals(category)
                || AdminPreference.CATEGORY_FORMAT.equals(category) || AdminPreference.CATEGORY_TECH_STACK.equals(category)
                || AdminPreference.CATEGORY_TOOL_STRATEGY.equals(category);
    }
}
