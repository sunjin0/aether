package com.aether.agent.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.dto.BusinessAgentRunCreateDto;
import com.aether.agent.dto.SessionMemoryFeedbackDto;
import com.aether.agent.dto.SessionMemoryCorrectionDto;
import com.aether.agent.dto.AgentControllerRequests.BusinessChat;
import com.aether.agent.dto.AgentControllerRequests.BusinessRun;
import com.aether.agent.dto.AgentControllerRequests.BusinessStream;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.AgentTask;
import com.aether.agent.entity.AgentTaskEvent;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.skill.dto.AgentArtifactQueryDto;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.service.AgentArtifactService;
import com.aether.agent.skill.vo.AgentArtifactVo;
import com.aether.agent.sandbox.service.SandboxTaskService;
import com.aether.agent.sandbox.vo.SandboxTaskVo;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentRunStepService;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentTaskEventService;
import com.aether.agent.service.AgentTaskService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.DeepAgentSigningClient;
import com.aether.agent.service.ChatAttachmentService;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.skill.service.SkillContextService;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.vo.AgentChatAttachmentVo;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.agent.vo.AgentConversationVo;
import com.aether.agent.vo.AgentRunStepVo;
import com.aether.agent.vo.AgentRunPlanVo;
import com.aether.agent.vo.BusinessAgentOptionVo;
import com.aether.agent.vo.BusinessAgentRunVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.storage.exception.ObjectNotFoundException;
import com.aether.storage.exception.ObjectStorageUnavailableException;
import com.aether.storage.service.ObjectStorageService;
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.dto.AgentWorkflowTaskListRequest;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
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
    /** 与 Admin 调试页同名常量同义：上限压得低，避免无上限分页成为放大攻击面。 */
    private static final int AGENT_WORKFLOW_TASK_DEFAULT_PAGE_SIZE = 20;
    private static final int AGENT_WORKFLOW_TASK_MAX_PAGE_SIZE = 50;

    private final ServiceAccountService serviceAccountService;
    private final AgentProductProfileService productProfileService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentChatService agentChatService;
    private final AgentRunService agentRunService;
    private final AgentRunStepService agentRunStepService;
    private final AgentRunPlanService planService;
    private final AgentConversationService conversationService;
    private final AgentMessageService messageService;
    private final DeepAgentRunService deepAgentRunService;
    private final ChatAttachmentService chatAttachmentService;
    private final AgentSessionService agentSessionService;
    private final AgentSessionMemoryService agentSessionMemoryService;
    private final AgentArtifactService agentArtifactService;
    private final SandboxTaskService sandboxTaskService;
    private final ObjectStorageService objectStorageService;
    private final String artifactBucket;
    private final AgentTaskService agentTaskService;
    private final AgentTaskEventService agentTaskEventService;
    private final DeepAgentSigningClient deepAgentSigningClient;
    private final KnowledgeContextService knowledgeContextService;
    private final SkillContextService skillContextService;
    private final AgentWorkflowTaskQueryService agentWorkflowTaskQueryService;
    private final ThreadPoolTaskExecutor executor;

    public BusinessAgentController(ServiceAccountService serviceAccountService,
                                   AgentProductProfileService productProfileService,
                                   AgentDefinitionService agentDefinitionService,
                                   AgentChatService agentChatService,
                                   AgentRunService agentRunService,
                                   AgentRunStepService agentRunStepService,
                                   AgentRunPlanService planService,
                                   AgentConversationService conversationService,
                                   AgentMessageService messageService,
                                   DeepAgentRunService deepAgentRunService,
                                   ChatAttachmentService chatAttachmentService,
                                   AgentSessionService agentSessionService,
                                   AgentSessionMemoryService agentSessionMemoryService,
                                   AgentArtifactService agentArtifactService,
                                   SandboxTaskService sandboxTaskService,
                                   ObjectStorageService objectStorageService,
                                   @Value("${artifact.storage.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String artifactBucket,
                                   AgentTaskService agentTaskService,
                                   AgentTaskEventService agentTaskEventService,
                                   DeepAgentSigningClient deepAgentSigningClient,
                                   KnowledgeContextService knowledgeContextService,
                                   SkillContextService skillContextService,
                                   AgentWorkflowTaskQueryService agentWorkflowTaskQueryService,
                                   @Qualifier("asyncPoolTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.serviceAccountService = serviceAccountService;
        this.productProfileService = productProfileService;
        this.agentDefinitionService = agentDefinitionService;
        this.agentChatService = agentChatService;
        this.agentRunService = agentRunService;
        this.planService = planService;
        this.agentRunStepService = agentRunStepService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.deepAgentRunService = deepAgentRunService;
        this.chatAttachmentService = chatAttachmentService;
        this.agentSessionService = agentSessionService;
        this.agentSessionMemoryService = agentSessionMemoryService;
        this.sandboxTaskService = sandboxTaskService;
        this.knowledgeContextService = knowledgeContextService;
        this.skillContextService = skillContextService;
        this.agentWorkflowTaskQueryService = agentWorkflowTaskQueryService;
        this.agentArtifactService = agentArtifactService;
        this.objectStorageService = objectStorageService;
        this.artifactBucket = artifactBucket;
        this.agentTaskService = agentTaskService;
        this.agentTaskEventService = agentTaskEventService;
        this.deepAgentSigningClient = deepAgentSigningClient;
        this.executor = executor;
    }

    /**
     * 查询当前服务账号通过已发布产品获授权的 Agent。
     */
    @ApiOperation("查询当前服务账号可调用 Agent")
    @GetMapping
    public WebResponse<List<BusinessAgentOptionVo>> agents() {
        ServiceAccount account = currentAccount();
        List<AgentProductProfile> products = allowedProducts(account, "AGENT");
        if (products.isEmpty()) return WebResponse.OK(Collections.<BusinessAgentOptionVo>emptyList());
        List<String> agentIds = products.stream().map(AgentProductProfile::getAgentDefinitionId)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        if (agentIds.isEmpty()) return WebResponse.OK(Collections.<BusinessAgentOptionVo>emptyList());
        List<AgentDefinition> agents = agentDefinitionService.list(Wrappers.lambdaQuery(AgentDefinition.class)
                .in(AgentDefinition::getId, agentIds).eq(AgentDefinition::getApplicationId, account.getApplicationId())
                .eq(AgentDefinition::getStatus, 1).eq(AgentDefinition::getDeleted, false)
                .orderByAsc(AgentDefinition::getName));
        return WebResponse.OK(agents.stream().map(this::toOption).collect(Collectors.toList()));
    }

    /**
     * 查询当前服务账号主体的对话历史。历史记录仍受当前产品授权约束：管理员撤销
     * Agent 授权后，该 Agent 的旧会话不再通过 Front 暴露。
     */
    @ApiOperation("查询当前服务账号主体的 Agent 会话")
    @GetMapping("/conversations")
    public WebResponse<List<AgentConversationVo>> conversations(
            @RequestParam(required = false) String agentId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "50") long pageSize) {
        List<String> allowedAgentIds = allowedAgentIds(currentAccount());
        if (allowedAgentIds.isEmpty()) return WebResponse.Page(Collections.<AgentConversationVo>emptyList(), 0L);
        Page<AgentConversation> page = conversationService.page(new Page<>(Math.max(1, current), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.lambdaQuery(AgentConversation.class)
                        .eq(AgentConversation::getUserId, currentPrincipalId())
                        .in(AgentConversation::getAgentDefinitionId, allowedAgentIds)
                        .eq(AgentConversation::getDeleted, false)
                        .orderByDesc(AgentConversation::getUpdatedAt));
        List<AgentConversationVo> values = page.getRecords().stream().map(item -> {
            AgentConversationVo value = new AgentConversationVo();
            BeanUtils.copyProperties(item, value);
            return value;
        }).collect(Collectors.toList());
        return WebResponse.Page(values, page.getTotal());
    }

    /** 查询当前服务账号主体可见会话的消息与调试字段。 */
    @ApiOperation("查询当前服务账号主体的会话消息")
    @GetMapping("/conversations/{conversationId}/messages")
    public WebResponse<List<AgentMessageVo>> conversationMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "100") long pageSize) {
        AgentConversation conversation = readableConversation(conversationId);
        Page<AgentMessage> page = messageService.page(new Page<>(Math.max(1, current), Math.min(Math.max(1, pageSize), 200)),
                Wrappers.lambdaQuery(AgentMessage.class)
                        .eq(AgentMessage::getConversationId, conversation.getId())
                        .eq(AgentMessage::getDeleted, false)
                        .orderByAsc(AgentMessage::getCreatedAt));
        List<AgentMessageVo> values = page.getRecords().stream().map(item -> {
            AgentMessageVo value = new AgentMessageVo();
            BeanUtils.copyProperties(item, value);
            return value;
        }).collect(Collectors.toList());
        return WebResponse.Page(values, page.getTotal());
    }

    /** 查询当前服务账号会话的可注入记忆。 */
    @ApiOperation("查询会话记忆")
    @GetMapping("/conversations/{conversationId}/memory")
    public WebResponse<List<AgentSessionMemory>> conversationMemory(@PathVariable String conversationId) {
        AgentConversation conversation = readableConversation(conversationId);
        AgentSession session = agentSessionService.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversation.getId())
                .eq(AgentSession::getDeleted, false), false);
        if (session == null) return WebResponse.OK(Collections.<AgentSessionMemory>emptyList());
        return WebResponse.OK(agentSessionMemoryService.listInjectable(session.getId(), 100));
    }

    /** 反馈当前服务账号会话中的记忆准确性。 */
    @ApiOperation("反馈会话记忆")
    @PostMapping("/conversations/{conversationId}/memory/feedback")
    public WebResponse<AgentSessionMemory> feedbackConversationMemory(@PathVariable String conversationId,
                                                                       @RequestBody SessionMemoryFeedbackDto body) {
        AgentConversation conversation = readableConversation(conversationId);
        if (body == null || StringUtils.isBlank(body.getMemoryId()) || StringUtils.isBlank(body.getVerdict()))
            throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
        AgentSession session = agentSessionService.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversation.getId()).eq(AgentSession::getUserId, currentPrincipalId())
                .eq(AgentSession::getDeleted, false), false);
        if (session == null) throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        AgentSessionMemory result = agentSessionMemoryService.feedback(session.getId(), body.getMemoryId(),
                body.getMemoryVersion(), body.getVerdict(), body.getReason());
        return WebResponse.OK(result);
    }

    /** 修正当前服务账号会话中的一条记忆，旧记录将按服务规则被替代。 */
    @ApiOperation("修正会话记忆")
    @PutMapping("/conversations/{conversationId}/memory/{memoryId}")
    public WebResponse<AgentSessionMemory> correctConversationMemory(@PathVariable String conversationId,
                                                                      @PathVariable String memoryId,
                                                                      @RequestBody SessionMemoryCorrectionDto body) {
        AgentSession session = readableSession(conversationId);
        if (body == null || StringUtils.isBlank(body.getContent()))
            throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
        return WebResponse.OK(agentSessionMemoryService.correctMemory(session.getId(), memoryId, body.getContent(),
                body.getReason(), body.getMemoryVersion()));
    }

    /** 从当前服务账号未来上下文中移除一条记忆。 */
    @ApiOperation("删除会话记忆")
    @DeleteMapping("/conversations/{conversationId}/memory/{memoryId}")
    public WebResponse<Void> deleteConversationMemory(@PathVariable String conversationId,
                                                       @PathVariable String memoryId,
                                                       @RequestParam(required = false) Integer memoryVersion,
                                                       @RequestParam(required = false) String reason) {
        AgentSession session = readableSession(conversationId);
        agentSessionMemoryService.deleteMemory(session.getId(), memoryId, memoryVersion, reason);
        return WebResponse.OK(null);
    }

    /** 查询可见 Deep 会话的持久任务快照与审计时间线。 */
    @ApiOperation("查询会话 Deep 任务时间线")
    @GetMapping("/conversations/{conversationId}/session")
    public WebResponse<Map<String, Object>> conversationSession(@PathVariable String conversationId) {
        AgentConversation conversation = readableConversation(conversationId);
        AgentSession session = agentSessionService.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversation.getId())
                .eq(AgentSession::getUserId, currentPrincipalId()).eq(AgentSession::getDeleted, false), false);
        Map<String, Object> result = new HashMap<String, Object>();
        if (session == null) {
            result.put("session", null); result.put("tasks", Collections.emptyList()); result.put("events", Collections.emptyList());
            return WebResponse.OK(result);
        }
        List<AgentTask> tasks = agentTaskService.list(Wrappers.lambdaQuery(AgentTask.class)
                .eq(AgentTask::getSessionId, session.getId()).eq(AgentTask::getUserId, currentPrincipalId())
                .eq(AgentTask::getDeleted, false).orderByAsc(AgentTask::getCreatedAt));
        List<AgentTaskEvent> events = new java.util.ArrayList<AgentTaskEvent>();
        for (AgentTask task : tasks) events.addAll(agentTaskEventService.listByTaskId(task.getId()));
        events.sort(java.util.Comparator.comparing(AgentTaskEvent::getOccurredAt,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        result.put("session", session); result.put("tasks", tasks); result.put("events", events);
        return WebResponse.OK(result);
    }

    /** 更新当前服务账号会话的工具确认策略。 */
    @ApiOperation("更新会话工具确认策略")
    @PutMapping("/conversations/{conversationId}/tool-approval-policy")
    public WebResponse<Void> updateToolApprovalPolicy(@PathVariable String conversationId,
                                                       @RequestBody Map<String, String> body) {
        AgentConversation conversation = readableConversation(conversationId);
        String policy = body == null ? null : body.get("toolApprovalPolicy");
        if (!"ask".equals(policy) && !"risky".equals(policy) && !"never".equals(policy))
            throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setToolApprovalPolicy(policy);
        conversationService.updateById(update);
        return WebResponse.OK(null);
    }

    /** 关闭当前服务账号自己的会话。 */
    @ApiOperation("关闭会话")
    @PutMapping("/conversations/{conversationId}/close")
    public WebResponse<Void> closeConversation(@PathVariable String conversationId) {
        AgentConversation conversation = readableConversation(conversationId);
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setStatus(1);
        conversationService.updateById(update);
        return WebResponse.OK(null);
    }

    /** 从当前服务账号的会话列表中移除会话。 */
    @ApiOperation("删除会话")
    @DeleteMapping("/conversations/{conversationId}")
    public WebResponse<Void> deleteConversation(@PathVariable String conversationId) {
        AgentConversation conversation = readableConversation(conversationId);
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setDeleted(true);
        conversationService.updateById(update);
        return WebResponse.OK(null);
    }

    /**
     * 会话的工作流任务清单：本次会话发起的工作流调用及其处理状态。
     *
     * <p>字段与 Admin 调试页的 {@code /api/agent/chat/conversation/{id}/workflow-tasks} 完全一致，
     * 业务前端因此可以复用同一套展示字段。作用域由会话归属推导，客户端无法借参数扩大可见范围；
     * 会话必须属于当前服务账号主体且其 Agent 仍在授权产品内。
     */
    @ApiOperation("查询会话的工作流任务")
    @PostMapping("/conversations/{conversationId}/workflow-tasks")
    public WebResponse<List<AgentWorkflowTaskVo>> conversationWorkflowTasks(
            @PathVariable String conversationId,
            @RequestBody(required = false) AgentWorkflowTaskListRequest query) {
        AgentConversation conversation = readableConversation(conversationId);
        AgentWorkflowTaskListRequest request = query == null ? new AgentWorkflowTaskListRequest() : query;
        // 白名单比 service 严：applyStateFilter 对未知取值是静默不筛，界面上选错值会悄悄返回全部。
        requireWorkflowTaskStateAllowed(request.getState());
        long current = request.getCurrent() == null ? 1L : request.getCurrent();
        long pageSize = request.getPageSize() == null ? AGENT_WORKFLOW_TASK_DEFAULT_PAGE_SIZE : request.getPageSize();
        if (current < 1L || pageSize < 1L || pageSize > AGENT_WORKFLOW_TASK_MAX_PAGE_SIZE)
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.tasks.page.size.invalid"));
        if (StringUtils.isNotBlank(request.getRunId())) requireRunInConversation(request.getRunId(), conversation.getId());
        AgentWorkflowTaskListOptions options = new AgentWorkflowTaskListOptions();
        options.setState(request.getState());
        // 必须显式赋值：options 的默认是「带上已结束的」，而未传 state 时沿用旧语义「只看未结束的」。
        options.setIncludeTerminal(Boolean.TRUE.equals(request.getIncludeTerminal()));
        options.setCurrent((int) Math.min(current, Integer.MAX_VALUE));
        options.setPageSize((int) pageSize);
        AgentWorkflowTaskPage page = agentWorkflowTaskQueryService.listByConversation(
                conversation.getId(), request.getRunId(), options);
        return WebResponse.Page(page.getTasks(), page.getTotal());
    }

    /**
     * 展示状态筛选取值白名单，取值与 Admin 调试页一致。
     */
    private void requireWorkflowTaskStateAllowed(String state) {
        if (StringUtils.isBlank(state)) return;
        String normalized = state.trim().toLowerCase();
        if (!"running".equals(normalized) && !"finished".equals(normalized) && !"all".equals(normalized))
            throw new ServerException(422, I18nUtils.getMessage("agent.workflow.tasks.state.invalid"));
    }

    /**
     * 运行必须属于本会话，否则按 404 处理：查询本身已按会话筛过运行，这里显式判是为了让
     * 「拿错了 ID」和「这个会话确实没有任务」区分开。
     */
    private void requireRunInConversation(String runId, String conversationId) {
        long owned = agentRunService.count(Wrappers.<AgentRun>query()
                .eq("id", runId).eq("conversation_id", conversationId));
        if (owned == 0L) throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
    }

    /**
     * 异步提交 Agent 运行。
     */
    @ApiOperation("外部系统异步提交 Agent 运行")
    @PostMapping("/{agentId}/runs")
    public WebResponse<BusinessAgentRunVo> run(@PathVariable String agentId,
                                                  @RequestBody BusinessRun request) {
        BusinessAgentRunCreateDto dto = request(request);
        String serviceAccountId = currentServiceAccountId();
        String principalId = currentPrincipalId();
        if (dto == null || StringUtils.isBlank(dto.getMessage()))
            throw new ServerException(422, "message is required");
        AgentRun existing = existingIdempotentRun(agentId, principalId, dto.getIdempotencyKey());
        if (existing != null) return WebResponse.OK(toVo(existing));
        serviceAccountService.assertAgentCallAllowed(serviceAccountId, agentId);
        AgentDefinition agent = agentChatService.getEnabledAgent(agentId);
        if ("DEEP".equals(agent.getExecutionMode())) {
            AgentConversation conversation = resolveConversation(agent, principalId, serviceAccountId, dto.getConversationId());
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
     * 同步执行标准 Agent，并在本次 HTTP 响应中返回最终回复。
     */
    @ApiOperation(value = "同步调用标准 Agent", notes = "仅支持 STANDARD Agent。Deep Agent 请调用 /{agentId}/runs 异步接口；重复使用同一 idempotencyKey 会返回已有运行结果。")
    @PostMapping("/{agentId}/chat")
    public WebResponse<BusinessAgentRunVo> chat(@PathVariable String agentId,
                                                 @RequestBody BusinessChat request) {
        BusinessAgentRunCreateDto dto = request(request);
        String serviceAccountId = currentServiceAccountId();
        String principalId = currentPrincipalId();
        if (dto == null || StringUtils.isBlank(dto.getMessage()))
            throw new ServerException(422, "message is required");
        AgentRun existing = existingIdempotentRun(agentId, principalId, dto.getIdempotencyKey());
        if (existing != null) return WebResponse.OK(toVo(existing));
        serviceAccountService.assertAgentCallAllowed(serviceAccountId, agentId);
        AgentDefinition agent = agentChatService.getEnabledAgent(agentId);
        if ("DEEP".equalsIgnoreCase(agent.getExecutionMode()))
            throw new ServerException(422, "Deep Agent 请使用异步运行接口 /api/business/agents/{agentId}/runs");
        AgentRun run;
        try {
            run = createQueuedRun(agent, principalId, dto);
        } catch (DuplicateKeyException ex) {
            AgentRun duplicate = existingIdempotentRun(agentId, principalId, dto.getIdempotencyKey());
            if (duplicate != null) return WebResponse.OK(toVo(duplicate));
            throw ex;
        }
        executeStandardRun(run.getId(), agentId, principalId, dto);
        return WebResponse.OK(toVo(agentRunService.getById(run.getId())));
    }

    /**
     * 流式调用 Agent。
     */
    @ApiOperation("外部系统流式调用 Agent")
    @PostMapping(value = "/{agentId}/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String agentId, @RequestBody BusinessStream request) {
        BusinessAgentRunCreateDto dto = request(request);
        String serviceAccountId = currentServiceAccountId();
        String principalId = currentPrincipalId();
        if (dto == null || (StringUtils.isBlank(dto.getMessage()) && StringUtils.isBlank(request.getParentMessageId())))
            throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
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
                    AgentConversation conversation = StringUtils.isNotBlank(request.getParentMessageId())
                            ? readableConversation(dto.getConversationId())
                            : resolveConversation(agent, principalId, serviceAccountId, dto.getConversationId());
                    if (StringUtils.isBlank(dto.getConversationId()) && StringUtils.isNotBlank(request.getToolApprovalPolicy())) {
                        String policy = request.getToolApprovalPolicy();
                        if (!"ask".equals(policy) && !"risky".equals(policy) && !"never".equals(policy))
                            throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
                        AgentConversation policyUpdate = new AgentConversation();
                        policyUpdate.setId(conversation.getId());
                        policyUpdate.setToolApprovalPolicy(policy);
                        conversationService.updateById(policyUpdate);
                    }
                    if (!StringUtils.equals(conversation.getAgentDefinitionId(), agentId))
                        throw new ServerException(422, I18nUtils.getMessage("agent.conversation.agent.mismatch"));
                    String runId;
                    if (StringUtils.isNotBlank(request.getParentMessageId())) {
                        runId = deepAgentRunService.resumeToolApproval(conversation.getId(), request.getParentMessageId(), principalId, request.getAnswer());
                    } else {
                        AgentChatDto chat = toChatDto(agentId, principalId, dto, request);
                        SkillRuntimeContext skillContext = skillContextService.resolve(agent, chat, dto.getMessage(), null);
                        List<ModelChatMessage> context = new ArrayList<>();
                        if (StringUtils.isNotBlank(skillContext.getSystemPrompt()))
                            context.add(new ModelChatMessage("system", skillContext.getSystemPrompt()));
                        String taskContext = buildDeepTaskContext(dto.getMessage(), request.getAttachmentContent());
                        List<Map<String, Object>> sources = knowledgeContextService.enhance(context, principalId,
                                conversation.getId(), agent.getId(), taskContext, skillContext.getKnowledgeBaseIds(), request.getRetrievalMode());
                        runId = deepAgentRunService.startRun(agent, principalId, conversation.getId(), dto.getMessage(),
                                request.getAttachmentContent(), request.getAttachments(), sources, skillContext, null);
                    }
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
                AgentChatDto chat = toChatDto(agentId, principalId, dto, request);
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
        return WebResponse.OK(toVo(readableRun(runId)));
    }

    /** 查询当前服务账号可见运行的调试时间线。 */
    @ApiOperation("查询 Agent 运行步骤")
    @GetMapping("/runs/{runId}/steps")
    public WebResponse<List<AgentRunStepVo>> steps(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        List<AgentRunStepVo> values = agentRunStepService.listByRunId(run.getId()).stream()
                .filter(item -> !"message.delta".equals(item.getEventType())).map(item -> {
                    AgentRunStepVo value = new AgentRunStepVo();
                    BeanUtils.copyProperties(item, value);
                    return value;
                }).collect(Collectors.toList());
        return WebResponse.OK(values);
    }

    /** 查询当前服务账号可见 Deep 运行的版本化计划。 */
    @ApiOperation("查询 Agent 运行计划")
    @GetMapping("/runs/{runId}/plan")
    public WebResponse<AgentRunPlanVo> plan(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        AgentRunPlanVo plan = planService.detail(runId);
        if (plan == null && StringUtils.isNotBlank(run.getTaskId())) plan = planService.detailByTaskId(run.getTaskId());
        if (plan == null) {
            plan = new AgentRunPlanVo();
            plan.setRunId(runId);
            plan.setStatus("PENDING");
            plan.setVersions(Collections.emptyList());
        }
        return WebResponse.OK(plan);
    }

    /**
     * 重新订阅一次已受理运行的步骤流。
     *
     * <p>与 Dashboard 调试页的 Deep 运行流一致：先重放已持久化的步骤，再继续推送新步骤，
     * 直到运行进入终态。断线重连或切换会话回来后可以据此恢复实时进度。</p>
     */
    @ApiOperation("重新订阅 Agent 运行步骤流")
    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeStream(@PathVariable String runId) {
        readableRun(runId);
        SseEmitter emitter = new SseEmitter(0L);
        ExternalStreamCallback callback = new ExternalStreamCallback(emitter);
        emitter.onCompletion(() -> callback.close());
        emitter.onTimeout(() -> callback.close());
        emitter.onError(error -> callback.close());
        executor.execute(() -> {
            try {
                AgentRun run = agentRunService.getById(runId);
                JSONObject accepted = new JSONObject();
                accepted.put("runId", runId);
                accepted.put("conversationId", run == null ? null : run.getConversationId());
                emitter.send(SseEmitter.event().name("accepted").data(accepted.toJSONString()));
                streamDeepRunFromStore(runId, callback);
            } catch (Exception ex) {
                completeWithError(emitter, ex);
            }
        });
        return emitter;
    }

    /** 为当前服务账号可见的持续任务提交评价。 */
    @ApiOperation("提交 Agent 任务反馈")
    @PostMapping("/tasks/{taskId}/feedback")
    public WebResponse<Void> taskFeedback(@PathVariable String taskId, @RequestBody Map<String, Object> payload) {
        AgentTask task = readableTask(taskId);
        int rating;
        try {
            rating = Integer.parseInt(String.valueOf(payload == null ? null : payload.get("rating")));
        } catch (Exception ignored) {
            throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
        }
        if (rating < 1 || rating > 5) throw new ServerException(422, I18nUtils.getMessage("agent.request.invalid"));
        String note = StringUtils.abbreviate(String.valueOf(payload == null ? "" : payload.getOrDefault("note", "")), 500);
        agentTaskEventService.record(task.getId(), task.getCurrentRunId(), "task.feedback",
                "rating=" + rating + (StringUtils.isBlank(note) ? "" : "；" + note));
        return WebResponse.OK(null);
    }

    /** 暂停当前服务账号正在执行的 Deep 运行。 */
    @ApiOperation("暂停 Deep Agent 运行")
    @PostMapping("/runs/{runId}/pause")
    public WebResponse<Void> pauseRun(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        if (!"DEEP".equals(run.getExecutionMode()))
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.run.cancel.unsupported"));
        deepAgentRunService.pause(runId, currentPrincipalId());
        return WebResponse.OK(null);
    }

    /** 恢复当前服务账号已暂停的 Deep 运行。 */
    @ApiOperation("恢复 Deep Agent 运行")
    @PostMapping("/runs/{runId}/resume")
    public WebResponse<Void> resumeRun(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        if (!"DEEP".equals(run.getExecutionMode()))
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.run.cancel.unsupported"));
        deepAgentRunService.resume(runId, currentPrincipalId());
        return WebResponse.OK(null);
    }

    /** 取消当前服务账号正在执行的 Deep 运行。 */
    @ApiOperation("取消 Deep Agent 运行")
    @PostMapping("/runs/{runId}/cancel")
    public WebResponse<Void> cancelRun(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        if (!"DEEP".equals(run.getExecutionMode()))
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.run.cancel.unsupported"));
        try {
            Map<String, String> request = new HashMap<String, String>();
            request.put("run_id", runId);
            deepAgentSigningClient.signedPost("/v1/runs/" + runId + "/cancel", request);
        } catch (Exception ex) {
            throw new ServerException(502, I18nUtils.getMessage("agent.deep.run.cancel.failed"));
        }
        return WebResponse.OK(null);
    }

    /** 查询当前服务账号在一次运行中生成的工件。 */
    @ApiOperation("查询运行生成工件")
    @GetMapping("/runs/{runId}/artifact")
    public WebResponse<AgentArtifact> artifact(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        AgentArtifact artifact = agentArtifactService.getOne(Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getRunId, run.getId())
                .eq(AgentArtifact::getUserId, currentPrincipalId())
                .isNull(AgentArtifact::getRecycledAt)
                .orderByDesc(AgentArtifact::getCreatedAt).last("limit 1"));
        return WebResponse.OK(artifact);
    }

    /** 查询当前服务账号运行关联的 Sandbox 任务。 */
    @ApiOperation("查询 Agent 运行的 Sandbox 任务")
    @GetMapping("/runs/{runId}/sandbox-task")
    public WebResponse<SandboxTaskVo> sandboxTask(@PathVariable String runId) {
        AgentRun run = readableRun(runId);
        return WebResponse.OK(sandboxTaskService.byRun(run.getId(), currentPrincipalId(), false));
    }

    /** 取消当前服务账号可见的 Sandbox 任务。 */
    @ApiOperation("取消 Sandbox 任务")
    @PostMapping("/sandbox-tasks/{taskId}/cancel")
    public WebResponse<Void> cancelSandboxTask(@PathVariable String taskId, @RequestBody(required = false) Map<String, Object> payload) {
        readableSandboxTask(taskId);
        String reason = payload == null ? null : StringUtils.abbreviate(String.valueOf(payload.getOrDefault("reason", "")), 500);
        sandboxTaskService.cancel(taskId, currentPrincipalId(), reason);
        return WebResponse.OK(null);
    }

    /** 重试当前服务账号可见的 Sandbox 任务。 */
    @ApiOperation("重试 Sandbox 任务")
    @PostMapping("/sandbox-tasks/{taskId}/retry")
    public WebResponse<SandboxTaskVo> retrySandboxTask(@PathVariable String taskId) {
        readableSandboxTask(taskId);
        return WebResponse.OK(sandboxTaskService.retry(taskId, currentPrincipalId()));
    }

    /** 预览当前服务账号可见工件。 */
    @ApiOperation("预览运行生成工件")
    @GetMapping("/artifacts/{artifactId}/preview")
    public ResponseEntity<byte[]> previewArtifact(@PathVariable String artifactId) {
        return artifactResponse(readableArtifact(artifactId), true);
    }

    /** 下载当前服务账号可见工件。 */
    @ApiOperation("下载运行生成工件")
    @GetMapping("/artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable String artifactId) {
        return artifactResponse(readableArtifact(artifactId), false);
    }

    /** 分页查询当前服务账号的生成文件库。 */
    @ApiOperation("查询我的生成文件")
    @PostMapping("/artifacts/list")
    public WebResponse<List<AgentArtifactVo>> artifacts(@RequestBody(required = false) AgentArtifactQueryDto query) {
        List<String> agentIds = allowedAgentIds(currentAccount());
        // 没有可用智能体就必然没有它的文件；同时也避免把空集合传进 pageOwned 拼出非法 SQL。
        if (agentIds.isEmpty()) return WebResponse.Page(Collections.emptyList(), 0L);
        Page<AgentArtifactVo> page = agentArtifactService.pageOwned(currentPrincipalId(), agentIds,
                query == null ? new AgentArtifactQueryDto() : query);
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /** 将生成文件移入回收站。 */
    @ApiOperation("将生成文件移入回收站")
    @DeleteMapping("/artifacts/{artifactId}")
    public WebResponse<Void> recycleArtifact(@PathVariable String artifactId) {
        // readableArtifact 已校验归属、授权智能体与未回收，这里只是把同一套规则复用在写入口上。
        readableArtifact(artifactId);
        agentArtifactService.recycle(artifactId, currentPrincipalId());
        return WebResponse.OK(I18nUtils.getMessage("agent.artifact.recycled"));
    }

    /** 恢复回收站里的生成文件。 */
    @ApiOperation("恢复生成文件")
    @PostMapping("/artifacts/{artifactId}/restore")
    public WebResponse<Void> restoreArtifact(@PathVariable String artifactId) {
        // readableArtifact 会把已回收的判成 404，所以恢复要单独按 recycled=true 取。
        AgentArtifact artifact = agentArtifactService.requireOwned(artifactId, currentPrincipalId(), true);
        if (!allowedAgentIds(currentAccount()).contains(artifact.getAgentDefinitionId()))
            throw new ServerException(404, I18nUtils.getMessage("agent.artifact.file-not-found"));
        agentArtifactService.restore(artifactId, currentPrincipalId());
        return WebResponse.OK(I18nUtils.getMessage("agent.artifact.restored"));
    }

    /** 上传并提取聊天附件；文件只会随同当前服务账号的下一次聊天请求使用。 */
    @ApiOperation("上传并识别聊天附件")
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WebResponse<List<AgentChatAttachmentVo>> uploadAttachments(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 3)
            throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachments.max.exceeded"));
        List<AgentChatAttachmentVo> values = files.stream().map(file -> {
            ChatAttachmentService.ChatAttachment attachment = chatAttachmentService.process(file);
            AgentChatAttachmentVo value = new AgentChatAttachmentVo();
            value.setFileName(attachment.getFileName());
            value.setContentType(attachment.getContentType());
            value.setSize(attachment.getSize());
            value.setObjectKey(attachment.getObjectKey());
            value.setExtractedContent(attachment.getExtractedContent());
            return value;
        }).collect(Collectors.toList());
        return WebResponse.OK(I18nUtils.getMessage("agent.chat.attachments.upload.success"), values);
    }

    /**
     * 回显当前租户自己的聊天附件原始文件。
     *
     * <p>与 Dashboard 调试页的 {@code /api/file/chat/preview} 同用途：上传接口只返回对象键，
     * 业务前端要能把已发出的附件重新打开。归属校验在 service 内，objectKey 不能越出租户的
     * {@code chat/} 前缀。</p>
     */
    @ApiOperation("预览聊天附件")
    @GetMapping("/attachments/preview")
    public ResponseEntity<byte[]> previewAttachment(@RequestParam("objectKey") String objectKey,
                                                     @RequestParam(value = "fileName", required = false) String fileName) {
        try {
            byte[] content = chatAttachmentService.readOwnedAttachment(objectKey);
            String name = StringUtils.defaultIfBlank(StringUtils.replaceEach(StringUtils.defaultIfBlank(fileName, ""),
                    new String[]{"\\", "/", "\r", "\n", "\""}, new String[]{"_", "_", "_", "_", "_"}), "attachment");
            ContentDisposition disposition = ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build();
            MediaType contentType;
            try {
                contentType = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
            } catch (RuntimeException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
            return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(contentType).contentLength(content.length).body(content);
        } catch (ObjectNotFoundException ex) {
            throw new ServerException(404, I18nUtils.getMessage("agent.chat.attachment.not-found"));
        } catch (ObjectStorageUnavailableException ex) {
            throw new ServerException(503, I18nUtils.getMessage("file.storage.unavailable"));
        }
    }

    private BusinessAgentRunCreateDto request(BusinessRun request) {
        return copyRequest(request);
    }

    private BusinessAgentRunCreateDto request(BusinessChat request) {
        return copyRequest(request);
    }

    private BusinessAgentRunCreateDto request(BusinessStream request) {
        return copyRequest(request);
    }

    private AgentRun readableRun(String runId) {
        AgentRun run = agentRunService.getById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        if (!StringUtils.equals(run.getUserId(), currentPrincipalId())
                || !allowedAgentIds(currentAccount()).contains(run.getAgentDefinitionId()))
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        return run;
    }

    private AgentTask readableTask(String taskId) {
        AgentTask task = agentTaskService.getById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        if (!StringUtils.equals(task.getUserId(), currentPrincipalId()) || !allowedAgentIds(currentAccount()).contains(task.getAgentDefinitionId()))
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return task;
    }

    private AgentArtifact readableArtifact(String artifactId) {
        AgentArtifact artifact = agentArtifactService.getById(artifactId);
        if (artifact == null || Boolean.TRUE.equals(artifact.getDeleted()) || artifact.getRecycledAt() != null)
            throw new ServerException(404, I18nUtils.getMessage("agent.artifact.file-not-found"));
        AgentRun run = readableRun(artifact.getRunId());
        if (!StringUtils.equals(artifact.getUserId(), currentPrincipalId())
                || !StringUtils.equals(artifact.getAgentDefinitionId(), run.getAgentDefinitionId()))
            throw new ServerException(404, I18nUtils.getMessage("agent.artifact.file-not-found"));
        return artifact;
    }

    private SandboxTaskVo readableSandboxTask(String taskId) {
        SandboxTaskVo task = sandboxTaskService.detail(taskId, currentPrincipalId(), false);
        if (task == null || StringUtils.isBlank(task.getRunId()))
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        readableRun(task.getRunId());
        return task;
    }

    private ResponseEntity<byte[]> artifactResponse(AgentArtifact artifact, boolean inline) {
        try {
            byte[] content = objectStorageService.getObject(artifactBucket, artifact.getObjectKey());
            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(artifact.getFileName(), StandardCharsets.UTF_8).build();
            MediaType contentType;
            try {
                contentType = StringUtils.isNotBlank(artifact.getContentType()) ? MediaType.parseMediaType(artifact.getContentType())
                        : MediaTypeFactory.getMediaType(artifact.getFileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
            } catch (RuntimeException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
            return ResponseEntity.ok().cacheControl(CacheControl.noCache()).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(contentType).contentLength(content.length).body(content);
        } catch (ObjectNotFoundException ex) {
            throw new ServerException(404, I18nUtils.getMessage("agent.artifact.file-not-found"));
        } catch (ObjectStorageUnavailableException ex) {
            throw new ServerException(503, I18nUtils.getMessage("file.storage.unavailable"));
        }
    }

    private BusinessAgentRunCreateDto copyRequest(Object request) {
        BusinessAgentRunCreateDto dto = new BusinessAgentRunCreateDto();
        if (request != null) org.springframework.beans.BeanUtils.copyProperties(request, dto);
        return dto;
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

    /** 将外部流式请求转换为与后台调试页一致的聊天上下文。 */
    private AgentChatDto toChatDto(String agentId, String principalId, BusinessAgentRunCreateDto dto, BusinessStream request) {
        AgentChatDto chat = new AgentChatDto();
        chat.setAgentId(agentId);
        chat.setConversationId(dto.getConversationId());
        chat.setMessage(dto.getMessage());
        chat.setUserId(principalId);
        chat.setParentMessageId(request.getParentMessageId());
        chat.setAnswer(request.getAnswer());
        chat.setInteractive(request.getInteractive());
        chat.setToolApprovalPolicy(request.getToolApprovalPolicy());
        chat.setThinking(request.getThinking());
        chat.setReasoningEffort(request.getReasoningEffort());
        chat.setRetrievalMode(request.getRetrievalMode());
        chat.setAttachmentContent(request.getAttachmentContent());
        chat.setAttachments(request.getAttachments());
        return chat;
    }

    /** Deep Agent 检索时使用附件正文，但不会把正文重复写入聊天消息。 */
    private String buildDeepTaskContext(String message, String attachmentContent) {
        return StringUtils.isBlank(attachmentContent) ? message
                : StringUtils.defaultString(message) + "\n\n附件内容：\n" + attachmentContent;
    }

    private AgentConversation resolveConversation(AgentDefinition agent, String principalId, String serviceAccountId,
                                                  String conversationId) {
        if (StringUtils.isNotBlank(conversationId)) {
            AgentConversation conversation = conversationService.getById(conversationId);
            if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())
                    || !StringUtils.equals(conversation.getUserId(), principalId)
                    || !StringUtils.equals(conversation.getAgentDefinitionId(), agent.getId()))
                throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
            return conversation;
        }
        AgentConversation conversation = new AgentConversation();
        conversation.setApplicationId(agent.getApplicationId());
        conversation.setUserId(principalId);
        conversation.setAgentDefinitionId(agent.getId());
        conversation.setServiceAccountId(serviceAccountId);
        conversation.setTitle("外部业务调用");
        conversation.setMessageCount(0);
        conversation.setStatus(0);
        conversation.setToolApprovalPolicy("ask");
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
        BusinessAgentOptionVo value = new BusinessAgentOptionVo();
        value.setId(agent.getId()); value.setName(agent.getName()); value.setCode(agent.getCode());
        value.setDescription(agent.getDescription()); value.setExecutionMode(agent.getExecutionMode());
        return value;
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
        if (!Boolean.TRUE.equals(account.getEnabled()))
            throw new ServerException(403, I18nUtils.getMessage("service-account.disabled"));
        return account;
    }

    private List<AgentProductProfile> allowedProducts(ServiceAccount account, String type) {
        if (StringUtils.isBlank(account.getAllowedProductIds())) return Collections.emptyList();
        List<String> ids;
        try { ids = JSON.parseArray(account.getAllowedProductIds(), String.class); } catch (RuntimeException ex) { return Collections.emptyList(); }
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return productProfileService.list(Wrappers.lambdaQuery(AgentProductProfile.class).in(AgentProductProfile::getId, ids)
                .eq(AgentProductProfile::getApplicationId, account.getApplicationId()).eq(AgentProductProfile::getProductType, type)
                .eq(AgentProductProfile::getStatus, 1).eq(AgentProductProfile::getDeleted, false));
    }

    private List<String> allowedAgentIds(ServiceAccount account) {
        return allowedProducts(account, "AGENT").stream()
                .map(AgentProductProfile::getAgentDefinitionId)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    private AgentConversation readableConversation(String conversationId) {
        AgentConversation conversation = conversationService.getOne(Wrappers.lambdaQuery(AgentConversation.class)
                .eq(AgentConversation::getId, conversationId)
                .eq(AgentConversation::getUserId, currentPrincipalId())
                .eq(AgentConversation::getDeleted, false));
        if (conversation == null || !allowedAgentIds(currentAccount()).contains(conversation.getAgentDefinitionId()))
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        return conversation;
    }

    private AgentSession readableSession(String conversationId) {
        AgentConversation conversation = readableConversation(conversationId);
        AgentSession session = agentSessionService.getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversation.getId()).eq(AgentSession::getUserId, currentPrincipalId())
                .eq(AgentSession::getDeleted, false), false);
        if (session == null) throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        return session;
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
            if (response != null) {
                data.put("model", response.getModel());
                data.put("promptTokens", response.getPromptTokens());
                data.put("completionTokens", response.getCompletionTokens());
                data.put("totalTokens", response.getTotalTokens());
                data.put("sources", response.getSources());
            }
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
