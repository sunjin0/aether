package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.dto.SessionMemoryCorrectionDto;
import com.aether.agent.dto.SessionMemoryFeedbackDto;
import com.aether.agent.dto.AgentControllerRequests.ConversationList;
import com.aether.agent.dto.AgentControllerRequests.ToolApprovalPolicy;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentRunContextMetricService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.ConversationCacheService;
import com.aether.agent.service.ConversationSummaryService;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.agent.vo.AgentConversationLifecycleVo;
import com.aether.agent.vo.AgentConversationContextVo;
import com.aether.agent.vo.AgentConversationVo;
import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import com.aether.agent.vo.AgentMessageStatisticsVo;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.agent.vo.AgentToolCallLogVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 会话管理 Controller
 */
@Api(tags = "会话管理 API")
@Validated
@RestController
@Permission(path = "/agent/conversation")
@RequestMapping("/api/agent/conversation")
public class AgentConversationController {
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final String IDEMPOTENCY_PROCESSING = "__PROCESSING__";
    private static final String SERVICE_ACCOUNT_PRINCIPAL_PREFIX = "sa:";

    private final AgentConversationService agentConversationService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentMessageService agentMessageService;
    private final AgentRunService agentRunService;
    private final AgentRunContextMetricService agentRunContextMetricService;
    private final AgentSessionService agentSessionService;
    private final AgentSessionMemoryService agentSessionMemoryService;
    private final AgentToolCallLogService agentToolCallLogService;
    private final AdminPreferenceEventService adminPreferenceEventService;
    private final AdminPreferenceService adminPreferenceService;
    private final ConversationCacheService conversationCacheService;
    private final ConversationSummaryService conversationSummaryService;
    private final AgentToolWorkflow agentToolWorkflow;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 {@code AgentConversationController} 实例。
     */
    @Autowired
    public AgentConversationController(AgentConversationService agentConversationService,
                                       AgentDefinitionService agentDefinitionService,
                                       AgentMessageService agentMessageService,
                                       AgentRunService agentRunService,
                                       AgentRunContextMetricService agentRunContextMetricService,
                                       AgentSessionService agentSessionService,
                                       AgentSessionMemoryService agentSessionMemoryService,
                                       AgentToolCallLogService agentToolCallLogService,
                                       AdminPreferenceEventService adminPreferenceEventService,
                                       AdminPreferenceService adminPreferenceService,
                                       ConversationCacheService conversationCacheService,
                                       ConversationSummaryService conversationSummaryService,
                                       AgentToolWorkflow agentToolWorkflow,
                                       @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.agentConversationService = agentConversationService;
        this.agentDefinitionService = agentDefinitionService;
        this.agentMessageService = agentMessageService;
        this.agentRunService = agentRunService;
        this.agentRunContextMetricService = agentRunContextMetricService;
        this.agentSessionService = agentSessionService;
        this.agentSessionMemoryService = agentSessionMemoryService;
        this.agentToolCallLogService = agentToolCallLogService;
        this.adminPreferenceEventService = adminPreferenceEventService;
        this.adminPreferenceService = adminPreferenceService;
        this.conversationCacheService = conversationCacheService;
        this.conversationSummaryService = conversationSummaryService;
        this.agentToolWorkflow = agentToolWorkflow;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保留既有手工构造调用；运行时使用包含上下文指标服务的完整构造器。
     */
    public AgentConversationController(AgentConversationService agentConversationService,
                                       AgentDefinitionService agentDefinitionService,
                                       AgentMessageService agentMessageService,
                                       AgentRunService agentRunService,
                                       AgentToolCallLogService agentToolCallLogService,
                                       AdminPreferenceEventService adminPreferenceEventService,
                                       AdminPreferenceService adminPreferenceService,
                                       ConversationCacheService conversationCacheService,
                                       ConversationSummaryService conversationSummaryService,
                                       AgentToolWorkflow agentToolWorkflow) {
        this(agentConversationService, agentDefinitionService, agentMessageService, agentRunService, null,
                null, null, agentToolCallLogService, adminPreferenceEventService, adminPreferenceService,
                conversationCacheService, conversationSummaryService, agentToolWorkflow, null);
    }

    /**
     * 会话列表。
     */
    @ApiOperation("会话列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentConversationVo>> list(@RequestBody ConversationList vo) {
        Page<AgentConversation> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentConversation> wrapper = Wrappers.lambdaQuery(AgentConversation.class)
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentConversation::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(vo.getStatus() != null, AgentConversation::getStatus, vo.getStatus())
                .eq(AgentConversation::getDeleted, false)
                .and(query -> query.eq(AgentConversation::getUserId, currentUserId())
                        .or().likeRight(AgentConversation::getUserId, SERVICE_ACCOUNT_PRINCIPAL_PREFIX))
                .orderByDesc(AgentConversation::getCreatedAt);
        Page<AgentConversation> result = agentConversationService.page(page, wrapper);
        Set<String> agentIds = result.getRecords().stream().map(AgentConversation::getAgentDefinitionId)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, AgentDefinition> agentsById = agentIds.isEmpty() ? Collections.emptyMap()
                : agentDefinitionService.listByIds(agentIds).stream()
                .collect(Collectors.toMap(AgentDefinition::getId, item -> item));
        List<AgentConversationVo> list = result.getRecords().stream().map(item -> {
            AgentConversationVo itemVo = new AgentConversationVo();
            BeanUtils.copyProperties(item, itemVo);
            itemVo.setExternal(isExternalConversation(item));
            AgentDefinition agent = agentsById.get(item.getAgentDefinitionId());
            if (agent != null) {
                itemVo.setAgentDefinitionName(agent.getName());
                itemVo.setExecutionMode(agent.getExecutionMode());
            }
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 详情当前请求。
     */
    @ApiOperation("会话详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentConversationVo> detail(@PathVariable @NotBlank String id) {
        AgentConversation conversation = getReadableConversation(id);
        AgentConversationVo vo = new AgentConversationVo();
        BeanUtils.copyProperties(conversation, vo);
        return WebResponse.OK(vo);
    }

    /**
     * 查询上下文组装、压缩和容量压力的运营聚合指标。
     */
    @ApiOperation("查询上下文运营指标")
    @GetMapping("/context/operations/metrics")
    public WebResponse<AgentContextOperationsMetricsVo> contextOperationsMetrics(
            @RequestParam(value = "sinceCreatedAt", required = false) Long sinceCreatedAt) {
        if (agentRunContextMetricService == null) {
            return WebResponse.OK(null);
        }
        return WebResponse.OK(agentRunContextMetricService.operationsMetrics(sinceCreatedAt));
    }

    /**
     * 查询会话消息。
     */
    @ApiOperation("查询会话消息")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/messages")
    public WebResponse<List<AgentMessageVo>> messages(@PathVariable @NotBlank String id,
                                                      @RequestParam(defaultValue = "1") Long current,
                                                      @RequestParam(defaultValue = "20") Long pageSize) {
        getReadableConversation(id);
        Page<AgentMessage> page = new Page<>(current, pageSize);
        Wrapper<AgentMessage> wrapper = Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, id)
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant", "tool")
                .orderByAsc(AgentMessage::getCreatedAt);
        Page<AgentMessage> result = agentMessageService.page(page, wrapper);
        List<AgentMessageVo> list = result.getRecords().stream().map(item -> {
            AgentMessageVo itemVo = new AgentMessageVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());

        // 聚合工具调用日志
        aggregateToolCallLogs(id, list);

        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 查询最近一次已完成调用的上下文容量度量。
     */
    @ApiOperation("查询会话上下文容量")
    @GetMapping("/{id}/context")
    public WebResponse<AgentConversationContextVo> context(@PathVariable @NotBlank String id) {
        getReadableConversation(id);
        if (agentRunContextMetricService == null) {
            return WebResponse.OK(null);
        }
        List<AgentRun> runs = agentRunService.list(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getConversationId, id)
                .eq(AgentRun::getDeleted, false)
                .orderByDesc(AgentRun::getCreatedAt));
        if (runs.isEmpty()) {
            return WebResponse.OK(null);
        }
        List<String> runIds = runs.stream().map(AgentRun::getId).collect(Collectors.toList());
        List<AgentRunContextMetric> metrics = agentRunContextMetricService.list(Wrappers.lambdaQuery(AgentRunContextMetric.class)
                .in(AgentRunContextMetric::getRunId, runIds)
                .eq(AgentRunContextMetric::getMetricPhase, "FINAL")
                .eq(AgentRunContextMetric::getDeleted, false)
                .orderByDesc(AgentRunContextMetric::getCreatedAt)
                .last("limit 1"));
        if (metrics.isEmpty()) {
            return WebResponse.OK(null);
        }
        AgentRunContextMetric metric = metrics.get(0);
        return WebResponse.OK(toContextVo(metric));
    }

    /**
     * 转换上下文容量度量。
     */
    private AgentConversationContextVo toContextVo(AgentRunContextMetric metric) {
        AgentConversationContextVo vo = new AgentConversationContextVo();
        BeanUtils.copyProperties(metric, vo);
        int used = metric.getPromptTokens() == null ? metric.getEstimatedPromptTokens() : metric.getPromptTokens();
        if (metric.getInputBudgetTokens() != null && metric.getInputBudgetTokens() > 0) {
            vo.setOccupancyPercent(Math.round(used * 10000.0D / metric.getInputBudgetTokens()) / 100.0D);
        }
        // Sections sum to the local message estimate; tool definitions are measured
        // separately. The residual versus the provider-reported total is chat-template
        // framing and tokenizer difference, exposed so the breakdown reconciles with
        // the occupancy ring. The tool-definition estimate is clamped so the sections
        // never overshoot the provider total.
        Integer toolDefinitionTokens = metric.getToolDefinitionTokens() == null
                ? 0 : metric.getToolDefinitionTokens();
        vo.setToolDefinitionTokens(toolDefinitionTokens);
        if (metric.getPromptTokens() != null && metric.getEstimatedPromptTokens() != null) {
            int room = Math.max(0, metric.getPromptTokens() - metric.getEstimatedPromptTokens());
            if (toolDefinitionTokens > room) {
                toolDefinitionTokens = room;
                vo.setToolDefinitionTokens(toolDefinitionTokens);
            }
            vo.setFramingTokens(Math.max(0,
                    metric.getPromptTokens() - metric.getEstimatedPromptTokens() - toolDefinitionTokens));
        } else {
            vo.setFramingTokens(0);
        }
        return vo;
    }

    /**
     * 查询会话可见记忆。
     */
    @ApiOperation("查询会话记忆")
    @GetMapping("/{id}/memory")
    public WebResponse<List<AgentSessionMemory>> memory(@PathVariable @NotBlank String id) {
        AgentConversation conversation = getReadableConversation(id);
        AgentSession session = requireSession(conversation);
        return WebResponse.OK(agentSessionMemoryService.listInjectable(session.getId(), 100));
    }

    /**
     * 通过取代旧记录修正记忆。
     */
    @ApiOperation("修正会话记忆")
    @Permission(path = "/agent/conversation", type = Permission.Type.Write)
    @PutMapping("/{id}/memory/{memoryId}")
    public WebResponse<AgentSessionMemory> correctMemory(@PathVariable @NotBlank String id,
                                                         @PathVariable @NotBlank String memoryId,
                                                         @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                         @RequestBody SessionMemoryCorrectionDto dto) {
        return withIdempotency(id, "correct:" + memoryId, idempotencyKey, () -> {
            AgentConversation conversation = getOwnedConversation(id);
            AgentSession session = requireSession(conversation);
            Integer version = expectedVersion(ifMatch, dto == null ? null : dto.getMemoryVersion());
            AgentSessionMemory replacement = agentSessionMemoryService.correctMemory(session.getId(), memoryId,
                    dto == null ? null : dto.getContent(), dto == null ? null : dto.getReason(), version);
            evictConversationMemoryAfterCommit(id, conversation.getUserId());
            return WebResponse.OK(replacement);
        });
    }

    /**
     * 从未来上下文移除记忆。
     */
    @ApiOperation("删除会话记忆")
    @Permission(path = "/agent/conversation", type = Permission.Type.Write)
    @DeleteMapping("/{id}/memory/{memoryId}")
    public WebResponse<Void> deleteMemory(@PathVariable @NotBlank String id,
                                          @PathVariable @NotBlank String memoryId,
                                          @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                          @RequestParam(value = "reason", required = false) String reason) {
        return withIdempotency(id, "delete:" + memoryId, idempotencyKey, () -> {
            AgentConversation conversation = getOwnedConversation(id);
            AgentSession session = requireSession(conversation);
            agentSessionMemoryService.deleteMemory(session.getId(), memoryId, expectedVersion(ifMatch, null), reason);
            evictConversationMemoryAfterCommit(id, conversation.getUserId());
            return WebResponse.OK((Void) null);
        });
    }

    /**
     * 反馈会话记忆准确性或过期状态。
     */
    @ApiOperation("反馈会话记忆")
    @Permission(path = "/agent/conversation", type = Permission.Type.Write)
    @PostMapping("/{id}/memory/feedback")
    public WebResponse<AgentSessionMemory> memoryFeedback(@PathVariable @NotBlank String id,
                                                          @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                          @RequestBody SessionMemoryFeedbackDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getMemoryId())) {
            throw new ServerException(400, "记忆反馈参数不完整");
        }
        return withIdempotency(id, "feedback:" + dto.getMemoryId(), idempotencyKey, () -> {
            AgentConversation conversation = getOwnedConversation(id);
            AgentSession session = requireSession(conversation);
            AgentSessionMemory result = agentSessionMemoryService.feedback(session.getId(), dto.getMemoryId(),
                    expectedVersion(ifMatch, dto.getMemoryVersion()), dto.getVerdict(), dto.getReason());
            evictConversationMemoryAfterCommit(id, conversation.getUserId());
            return WebResponse.OK(result);
        });
    }

    /**
     * 批量聚合工具调用日志到 assistant 消息。
     * 查询逻辑：messageId -> agent_run -> agent_tool_call_log
     */
    private void aggregateToolCallLogs(String conversationId, List<AgentMessageVo> messageList) {
        // 1. 收集当前页所有 assistant 消息的 ID
        List<String> assistantMessageIds = messageList.stream()
                .filter(msg -> "assistant".equals(msg.getRole()))
                .map(AgentMessageVo::getId)
                .collect(Collectors.toList());

        if (assistantMessageIds.isEmpty()) {
            return;
        }

        // 2. 一次性查询所有关联的 run（message_id in assistantMessageIds）
        List<AgentRun> runs = agentRunService.list(Wrappers.lambdaQuery(AgentRun.class)
                .in(AgentRun::getMessageId, assistantMessageIds)
                .eq(AgentRun::getConversationId, conversationId)
                .eq(AgentRun::getDeleted, false));

        if (runs.isEmpty()) {
            return;
        }

        // 3. 建立 messageId -> run 映射
        Map<String, AgentRun> messageToRunMap = new HashMap<>();
        for (AgentRun run : runs) {
            // 一个 message 可能对应多个 run（重试场景），取第一个
            messageToRunMap.putIfAbsent(run.getMessageId(), run);
        }

        // 4. 一次性查询所有 run 的 tool_call_log 和最终上下文指标
        List<String> runIds = runs.stream()
                .map(AgentRun::getId)
                .distinct()
                .collect(Collectors.toList());

        List<AgentToolCallLog> allLogs = agentToolCallLogService.list(Wrappers.lambdaQuery(AgentToolCallLog.class)
                .in(AgentToolCallLog::getRunId, runIds)
                .eq(AgentToolCallLog::getDeleted, false)
                .orderByAsc(AgentToolCallLog::getCreatedAt));

        List<AgentRunContextMetric> finalMetrics = agentRunContextMetricService == null
                ? Collections.emptyList()
                : agentRunContextMetricService.list(Wrappers.lambdaQuery(AgentRunContextMetric.class)
                .in(AgentRunContextMetric::getRunId, runIds)
                .eq(AgentRunContextMetric::getMetricPhase, "FINAL")
                .eq(AgentRunContextMetric::getDeleted, false)
                .orderByDesc(AgentRunContextMetric::getCreatedAt));

        // 5. 建立 runId -> logs 映射
        Map<String, List<AgentToolCallLogVo>> runToLogsMap = new HashMap<>();
        for (AgentToolCallLog log : allLogs) {
            AgentToolCallLogVo logVo = new AgentToolCallLogVo();
            BeanUtils.copyProperties(log, logVo);
            runToLogsMap.computeIfAbsent(log.getRunId(), k -> new ArrayList<>()).add(logVo);
        }
        Map<String, AgentConversationContextVo> runToContextMetricMap = new HashMap<>();
        for (AgentRunContextMetric metric : finalMetrics) {
            runToContextMetricMap.putIfAbsent(metric.getRunId(), toContextVo(metric));
        }

        // 6. 组装到 AgentMessageVo
        for (AgentMessageVo msgVo : messageList) {
            if ("assistant".equals(msgVo.getRole())) {
                AgentRun run = messageToRunMap.get(msgVo.getId());
                if (run != null) {
                    msgVo.setRunId(run.getId());
                    List<AgentToolCallLogVo> logs = runToLogsMap.get(run.getId());
                    msgVo.setToolCallLogs(logs != null ? logs : Collections.emptyList());
                    msgVo.setContextMetric(runToContextMetricMap.get(run.getId()));
                }
            }
        }
    }

    /**
     * 更新ToolApprovalPolicy。
     */
    @ApiOperation("更新会话工具确认策略")
    @Permission(path = "/agent/conversation", type = Permission.Type.Write)
    @PutMapping("/{id}/tool-approval-policy")
    public WebResponse<Void> updateToolApprovalPolicy(@PathVariable @NotBlank String id,
                                                       @RequestBody ToolApprovalPolicy body) {
        AgentConversation conversation = getOwnedConversation(id);
        String policy = body == null ? null : body.getToolApprovalPolicy();
        if (!"ask".equals(policy) && !"risky".equals(policy) && !"never".equals(policy)) {
            throw new ServerException(422, "不支持的工具确认策略");
        }
        AgentConversation update = new AgentConversation();
        update.setId(id);
        update.setToolApprovalPolicy(policy);
        agentConversationService.updateById(update);
        // A previous "allow for 10 minutes" choice must not survive a policy change.
        agentToolWorkflow.revokeTemporaryGrants(conversation.getUserId(), conversation.getAgentDefinitionId(), conversation.getId());
        return WebResponse.OK(null);
    }

    /**
     * 关闭当前资源。
     */
    @ApiOperation("关闭会话")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/conversation", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}/close")
    public WebResponse<Void> close(@PathVariable @NotBlank String id) {
        getOwnedConversation(id);
        AgentConversation conversation = new AgentConversation();
        conversation.setId(id);
        conversation.setStatus(1); // 关闭
        boolean updated = agentConversationService.updateById(conversation);
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.conversation.close.success") : I18nUtils.getMessage("agent.conversation.close.fail"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除会话")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true, dataType = "string", paramType = "header"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/conversation", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        AgentConversation conversation = getOwnedConversation(id);
        List<AdminPreferenceEvent> preferenceEvents = adminPreferenceEventService.list(
                Wrappers.lambdaQuery(AdminPreferenceEvent.class)
                        .eq(AdminPreferenceEvent::getConversationId, id)
                        .eq(AdminPreferenceEvent::getDeleted, false));
        List<String> affectedPreferenceIds = preferenceEvents.stream()
                .map(AdminPreferenceEvent::getPreferenceId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        List<AgentRun> runs = agentRunService.list(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getConversationId, id)
                .eq(AgentRun::getDeleted, false));
        if (!runs.isEmpty()) {
            List<String> runIds = runs.stream().map(AgentRun::getId).collect(Collectors.toList());
            agentToolCallLogService.remove(Wrappers.lambdaQuery(AgentToolCallLog.class)
                    .in(AgentToolCallLog::getRunId, runIds));
        }
        agentRunService.remove(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getConversationId, id));
        agentMessageService.remove(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, id));
        adminPreferenceEventService.remove(Wrappers.lambdaQuery(AdminPreferenceEvent.class)
                .eq(AdminPreferenceEvent::getConversationId, id));
        adminPreferenceService.reconcileAfterEvidenceRemoval(affectedPreferenceIds);
        boolean removed = agentConversationService.removeById(id);
        if (!removed) {
            throw new ServerException(500, I18nUtils.getMessage("agent.conversation.delete.fail"));
        }
        evictConversationMemoryAfterCommit(id, conversation.getUserId());
        return WebResponse.OK(I18nUtils.getMessage("agent.conversation.delete.success"));
    }

    /**
     * 会话生命周期查询。
     */
    @ApiOperation("会话生命周期查询")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true, dataType = "string", paramType = "header"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/lifecycle")
    public WebResponse<AgentConversationLifecycleVo> lifecycle(@PathVariable @NotBlank String id) {
        getReadableConversation(id);
        AgentConversationLifecycleVo lifecycle = agentConversationService.getLifecycle(id);
        if (lifecycle == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.lifecycle.not-found"));
        }
        return WebResponse.OK(lifecycle);
    }

    /**
     * 会话消息统计。
     */
    @ApiOperation("会话消息统计")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true, dataType = "string", paramType = "header"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/statistics")
    public WebResponse<AgentMessageStatisticsVo> statistics(@PathVariable @NotBlank String id) {
        getReadableConversation(id);
        AgentMessageStatisticsVo statistics = agentConversationService.getStatistics(id);
        if (statistics == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.statistics.not-found"));
        }
        return WebResponse.OK(statistics);
    }

    /**
     * 获取Owned会话。
     */
    private AgentConversation getOwnedConversation(String id) {
        AgentConversation conversation = agentConversationService.getOne(Wrappers.lambdaQuery(AgentConversation.class)
                .eq(AgentConversation::getId, id)
                .eq(AgentConversation::getDeleted, false)
                .eq(AgentConversation::getUserId, currentUserId()));
        if (conversation == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        return conversation;
    }

    /** 后台管理员可只读查看服务账号创建的外部调用会话。 */
    private AgentConversation getReadableConversation(String id) {
        AgentConversation conversation = agentConversationService.getOne(Wrappers.lambdaQuery(AgentConversation.class)
                .eq(AgentConversation::getId, id)
                .eq(AgentConversation::getDeleted, false));
        if (conversation == null || (!StringUtils.equals(conversation.getUserId(), currentUserId())
                && !isExternalConversation(conversation))) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        return conversation;
    }

    private String currentUserId() {
        return CurrentUser.getUser().get("userId");
    }

    private boolean isExternalConversation(AgentConversation conversation) {
        return conversation != null && StringUtils.startsWith(conversation.getUserId(), SERVICE_ACCOUNT_PRINCIPAL_PREFIX);
    }

    /**
     * 查询会话对应的统一 Session。
     */
    private AgentSession requireSession(AgentConversation conversation) {
        if (agentSessionService == null || agentSessionMemoryService == null) {
            throw new ServerException(500, "Agent Session 服务不可用");
        }
        AgentSession session = agentSessionService.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversation.getId())
                .eq(AgentSession::getUserId, conversation.getUserId())
                .eq(AgentSession::getDeleted, false));
        if (session == null) {
            session = agentSessionService.getOrCreate(conversation.getId(), conversation.getUserId(),
                    conversation.getAgentDefinitionId());
        }
        return session;
    }

    /**
     * 解析乐观版本。
     */
    private Integer expectedVersion(String ifMatch, Integer fallback) {
        if (StringUtils.isBlank(ifMatch)) {
            return fallback;
        }
        String normalized = StringUtils.remove(StringUtils.trimToEmpty(ifMatch), '"');
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException e) {
            throw new ServerException(400, "If-Match 必须是记忆版本号");
        }
    }

    /**
     * 执行带 HTTP 幂等键的记忆写入。
     */
    @SuppressWarnings("unchecked")
    private <T> WebResponse<T> withIdempotency(String conversationId, String action,
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
        String userId = CurrentUser.getUser().get("userId");
        String cacheKey = "agent:conversation:memory:idempotency:" + userId + ":" + conversationId
                + ":" + action + ":" + normalized;
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
     * 处理evict会话MemoryAfterCommit。
     */
    private void evictConversationMemoryAfterCommit(String conversationId, String userId) {
        Runnable cleanup = () -> {
            conversationCacheService.evict(conversationId);
            conversationSummaryService.evict(conversationId);
            adminPreferenceService.clearUserCache(userId);
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        /**
                         * 处理afterCommit。
                         */
                        @Override
                        public void afterCommit() {
                            cleanup.run();
                        }
                    });
        } else {
            cleanup.run();
        }
    }
}
