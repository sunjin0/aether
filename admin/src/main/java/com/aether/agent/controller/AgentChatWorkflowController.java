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
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
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
     *
     * <p>传 {@code state} 可以切成「全部 / 进行中 / 已结束」三档，此时它接管 {@code includeTerminal}。
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
        requireStateAllowed(request.getState());
        long current = request.getCurrent() == null ? 1L : request.getCurrent();
        long pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (current < 1L || pageSize < 1L || pageSize > MAX_PAGE_SIZE) {
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.tasks.page.size.invalid"));
        }
        if (StringUtils.isNotBlank(request.getRunId())) requireRunInConversation(request.getRunId(), conversationId);
        AgentWorkflowTaskListOptions options = new AgentWorkflowTaskListOptions();
        options.setState(request.getState());
        // 必须显式赋值：options 的默认是「带上已结束的」，而本接口不传时沿用旧语义「只看未结束的」。
        options.setIncludeTerminal(Boolean.TRUE.equals(request.getIncludeTerminal()));
        options.setCurrent((int) Math.min(current, Integer.MAX_VALUE));
        options.setPageSize((int) pageSize);
        AgentWorkflowTaskPage page = taskQueryService.listByConversation(conversationId, request.getRunId(), options);
        return WebResponse.Page(page.getTasks(), page.getTotal());
    }

    /**
     * 状态筛选取值白名单。
     *
     * <p>刻意比 service 严：{@code applyStateFilter} 对未知取值是「静默不筛」，那对界面传错值来说
     * 等于悄悄返回全部，用户会以为筛选生效了。
     */
    private void requireStateAllowed(String state) {
        if (StringUtils.isBlank(state)) return;
        String normalized = state.trim().toLowerCase();
        if (!"running".equals(normalized) && !"finished".equals(normalized) && !"all".equals(normalized)) {
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.tasks.state.invalid"));
        }
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
        String userId = CurrentUser.userId();
        if (StringUtils.isBlank(userId)) throw new ServerException(401, I18nUtils.getMessage("agent.unauthorized"));
        return userId;
    }
}
