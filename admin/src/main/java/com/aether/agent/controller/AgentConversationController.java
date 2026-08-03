package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.ConversationCacheService;
import com.aether.agent.service.ConversationSummaryService;
import com.aether.agent.vo.AgentConversationLifecycleVo;
import com.aether.agent.vo.AgentConversationVo;
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

    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final AgentRunService agentRunService;
    private final AgentToolCallLogService agentToolCallLogService;
    private final AdminPreferenceEventService adminPreferenceEventService;
    private final AdminPreferenceService adminPreferenceService;
    private final ConversationCacheService conversationCacheService;
    private final ConversationSummaryService conversationSummaryService;

    @Autowired
    public AgentConversationController(AgentConversationService agentConversationService,
                                       AgentMessageService agentMessageService,
                                       AgentRunService agentRunService,
                                       AgentToolCallLogService agentToolCallLogService,
                                       AdminPreferenceEventService adminPreferenceEventService,
                                       AdminPreferenceService adminPreferenceService,
                                       ConversationCacheService conversationCacheService,
                                       ConversationSummaryService conversationSummaryService) {
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.agentRunService = agentRunService;
        this.agentToolCallLogService = agentToolCallLogService;
        this.adminPreferenceEventService = adminPreferenceEventService;
        this.adminPreferenceService = adminPreferenceService;
        this.conversationCacheService = conversationCacheService;
        this.conversationSummaryService = conversationSummaryService;
    }

    @ApiOperation("会话列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentConversationVo>> list(@RequestBody AgentConversationVo vo) {
        Page<AgentConversation> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentConversation> wrapper = Wrappers.lambdaQuery(AgentConversation.class)
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentConversation::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(vo.getStatus() != null, AgentConversation::getStatus, vo.getStatus())
                .eq(AgentConversation::getDeleted, false)
                .eq(AgentConversation::getUserId, CurrentUser.getUser().get("userId"))
                .orderByDesc(AgentConversation::getCreatedAt);
        Page<AgentConversation> result = agentConversationService.page(page, wrapper);
        List<AgentConversationVo> list = result.getRecords().stream().map(item -> {
            AgentConversationVo itemVo = new AgentConversationVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("会话详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentConversationVo> detail(@PathVariable @NotBlank String id) {
        AgentConversation conversation = getOwnedConversation(id);
        AgentConversationVo vo = new AgentConversationVo();
        BeanUtils.copyProperties(conversation, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("查询会话消息")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/messages")
    public WebResponse<List<AgentMessageVo>> messages(@PathVariable @NotBlank String id,
                                                       @RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "20") Long pageSize) {
        getOwnedConversation(id);
        Page<AgentMessage> page = new Page<>(current, pageSize);
        Wrapper<AgentMessage> wrapper = Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, id)
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant")
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

        // 4. 一次性查询所有 run 的 tool_call_log
        List<String> runIds = runs.stream()
                .map(AgentRun::getId)
                .distinct()
                .collect(Collectors.toList());

        List<AgentToolCallLog> allLogs = agentToolCallLogService.list(Wrappers.lambdaQuery(AgentToolCallLog.class)
                .in(AgentToolCallLog::getRunId, runIds)
                .eq(AgentToolCallLog::getDeleted, false)
                .orderByAsc(AgentToolCallLog::getCreatedAt));

        // 5. 建立 runId -> logs 映射
        Map<String, List<AgentToolCallLogVo>> runToLogsMap = new HashMap<>();
        for (AgentToolCallLog log : allLogs) {
            AgentToolCallLogVo logVo = new AgentToolCallLogVo();
            BeanUtils.copyProperties(log, logVo);
            runToLogsMap.computeIfAbsent(log.getRunId(), k -> new ArrayList<>()).add(logVo);
        }

        // 6. 组装到 AgentMessageVo
        for (AgentMessageVo msgVo : messageList) {
            if ("assistant".equals(msgVo.getRole())) {
                AgentRun run = messageToRunMap.get(msgVo.getId());
                if (run != null) {
                    msgVo.setRunId(run.getId());
                    List<AgentToolCallLogVo> logs = runToLogsMap.get(run.getId());
                    msgVo.setToolCallLogs(logs != null ? logs : Collections.emptyList());
                }
            }
        }
    }

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

    @ApiOperation("会话生命周期查询")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true, dataType = "string", paramType = "header"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/lifecycle")
    public WebResponse<AgentConversationLifecycleVo> lifecycle(@PathVariable @NotBlank String id) {
        getOwnedConversation(id);
        AgentConversationLifecycleVo lifecycle = agentConversationService.getLifecycle(id);
        if (lifecycle == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.lifecycle.not-found"));
        }
        return WebResponse.OK(lifecycle);
    }

    @ApiOperation("会话消息统计")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "会话ID", required = true, dataType = "string", paramType = "header"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/statistics")
    public WebResponse<AgentMessageStatisticsVo> statistics(@PathVariable @NotBlank String id) {
        getOwnedConversation(id);
        AgentMessageStatisticsVo statistics = agentConversationService.getStatistics(id);
        if (statistics == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.statistics.not-found"));
        }
        return WebResponse.OK(statistics);
    }

    private AgentConversation getOwnedConversation(String id) {
        AgentConversation conversation = agentConversationService.getOne(Wrappers.lambdaQuery(AgentConversation.class)
                .eq(AgentConversation::getId, id)
                .eq(AgentConversation::getDeleted, false)
                .eq(AgentConversation::getUserId, CurrentUser.getUser().get("userId")));
        if (conversation == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        return conversation;
    }

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
