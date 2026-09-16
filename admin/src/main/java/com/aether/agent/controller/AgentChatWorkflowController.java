package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentRunService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.workflow.dto.AgentWorkflowTaskListRequest;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聊天页的「工作任务列表」：本次会话发起的工作流调用及其处理状态。
 *
 * <p>权限挂在 {@code /agent/chat} 而不是 {@code /workflow/run} —— 本功能的目标用户是聊天页的
 * 使用者，他们没有工作流编排台的权限；用后者门控会把功能直接 403 掉。行级可见性由会话归属兜底。
 */
@Api(tags = "Agent 聊天工作流任务 API")
@Validated
@RestController
@Permission(path = "/agent/chat")
@RequestMapping("/api/agent/chat/conversation")
public class AgentChatWorkflowController {
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 上限压得很低：这是给人看的滚动列表，无上限的分页会成为放大攻击面。 */
    private static final int MAX_PAGE_SIZE = 50;

    private final AgentConversationService conversationService;
    private final AgentRunService runService;
    private final AgentWorkflowTaskQueryService taskQueryService;

    public AgentChatWorkflowController(AgentConversationService conversationService, AgentRunService runService,
                                       AgentWorkflowTaskQueryService taskQueryService) {
        this.conversationService = conversationService;
        this.runService = runService;
        this.taskQueryService = taskQueryService;
    }

    /**
     * 会话的工作流任务列表。默认只返回尚未结束的任务，供聊天页在流式过程中展示。
     */
    @ApiOperation("查询会话的工作流任务")
    @PostMapping("/{conversationId}/workflow-tasks")
    public WebResponse<List<AgentWorkflowTaskVo>> tasks(@PathVariable String conversationId,
                                                        @RequestBody(required = false) AgentWorkflowTaskListRequest query) {
        String userId = currentUserId();
        AgentConversation conversation = conversationService.getById(conversationId);
        // 不存在与非本人回同一个 404：区分开就等于把「这个会话存在」告诉了无关的人。
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())
                || !userId.equals(conversation.getUserId())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        AgentWorkflowTaskListRequest request = query == null ? new AgentWorkflowTaskListRequest() : query;
        long current = request.getCurrent() == null ? 1L : request.getCurrent();
        long pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (current < 1L || pageSize < 1L || pageSize > MAX_PAGE_SIZE) {
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.tasks.page.size.invalid"));
        }
        if (StringUtils.isNotBlank(request.getRunId())) requireRunInConversation(request.getRunId(), conversationId);
        AgentWorkflowTaskPage page = taskQueryService.listByConversation(conversationId, request.getRunId(),
                Boolean.TRUE.equals(request.getIncludeTerminal()), (int) current, (int) pageSize);
        return WebResponse.Page(page.getTasks(), page.getTotal());
    }

    /**
     * 运行必须属于本会话。
     *
     * <p>查询本身已按会话筛过运行，越权的 {@code runId} 只会查出空列表；这里显式判 404 是为了
     * 让「你拿错了 ID」和「这个会话确实没有任务」区分开，也避免把不存在的运行当成合法入参。
     */
    private void requireRunInConversation(String runId, String conversationId) {
        long owned = runService.count(Wrappers.<AgentRun>query()
                .eq("id", runId).eq("conversation_id", conversationId));
        if (owned == 0L) throw new ServerException(404, I18nUtils.getMessage("agent.run.not.found"));
    }

    private String currentUserId() {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) throw new ServerException(401, I18nUtils.getMessage("agent.unauthorized"));
        return userId;
    }
}
