package com.aether.agent.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.dto.AgentControllerRequests.ConversationList;
import com.aether.agent.observability.ChatLatencyMetrics;
import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.ChatAttachmentService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.service.RuntimeEmailCredentialStore;
import com.aether.agent.skill.service.SkillContextService;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.runtime.DeepRunEventHub;
import com.aether.agent.vo.AgentConversationVo;
import com.aether.agent.vo.AgentChatAttachmentVo;
import com.aether.agent.vo.AgentMessageVo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
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
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Agent聊天 Controller。
 */
@Api(tags = "Agent聊天 API")
@Validated
@RestController
@Permission(path = "/agent/chat")
@RequestMapping("/api/agent/chat")
public class AgentChatController {

    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);
    private static final long STREAM_TIMEOUT_MS = 300000L; // 5分钟，推理模型需要更长响应时间
    private static final long DEEP_STREAM_TIMEOUT_MARGIN_SECONDS = 30L;
    private static final long DEFAULT_DEEP_RUN_TIMEOUT_SECONDS = 600L;
    private static final long HEARTBEAT_INTERVAL_MS = 15000L; // 15秒心跳
    private static final int STREAM_ACTIVE_WARNING_THRESHOLD = 28;
    private static final int STREAM_QUEUE_WARNING_THRESHOLD = 160;

    private final AgentChatService agentChatService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final ChatAttachmentService chatAttachmentService;
    private final AgentDefinitionService agentDefinitionService;
    private final DeepAgentRunService deepAgentRunService;
    private final DeepAgentCallbackController deepAgentCallbackController;
    private final KnowledgeContextService knowledgeContextService;
    private final DeepAgentConfig deepAgentConfig;
    private final SkillContextService skillContextService;
    private final ModelProviderService modelProviderService;
    private final ModelCatalogService modelCatalogService;
    private final DeepRunEventHub deepRunEventHub;
    @Autowired(required = false)
    private RuntimeEmailCredentialStore runtimeEmailCredentialStore;
    /**
     * Bounded SSE worker pool. Core threads match the intended concurrent-stream capacity:
     * {@link ThreadPoolExecutor} fills a queue before expanding beyond its core size.
     */
    private final ThreadPoolExecutor streamExecutor = createStreamExecutor();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

    private static ThreadPoolExecutor createStreamExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(32, 32, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(200), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * 创建 {@code AgentChatController} 实例。
     */
    @Autowired
    public AgentChatController(AgentChatService agentChatService, AgentConversationService agentConversationService,
                               AgentMessageService agentMessageService, ChatAttachmentService chatAttachmentService,
                                AgentDefinitionService agentDefinitionService, DeepAgentRunService deepAgentRunService,
                                DeepAgentCallbackController deepAgentCallbackController, KnowledgeContextService knowledgeContextService,
                                DeepAgentConfig deepAgentConfig, SkillContextService skillContextService,
                                ModelProviderService modelProviderService, ModelCatalogService modelCatalogService,
                                DeepRunEventHub deepRunEventHub) {
        this.agentChatService = agentChatService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.chatAttachmentService = chatAttachmentService;
        this.agentDefinitionService = agentDefinitionService;
        this.deepAgentRunService = deepAgentRunService;
        this.deepAgentCallbackController = deepAgentCallbackController;
        this.knowledgeContextService = knowledgeContextService;
        this.deepAgentConfig = deepAgentConfig;
        this.skillContextService = skillContextService;
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
        this.deepRunEventHub = deepRunEventHub;
    }

    /**
     * 兼容既有控制器单元测试；生产环境始终使用注入 Skill 上下文服务的完整构造器。
     */
    public AgentChatController(AgentChatService agentChatService, AgentConversationService agentConversationService,
                               AgentMessageService agentMessageService, ChatAttachmentService chatAttachmentService,
                               AgentDefinitionService agentDefinitionService, DeepAgentRunService deepAgentRunService,
                               DeepAgentCallbackController deepAgentCallbackController, KnowledgeContextService knowledgeContextService,
                               DeepAgentConfig deepAgentConfig) {
        this(agentChatService, agentConversationService, agentMessageService, chatAttachmentService, agentDefinitionService,
                deepAgentRunService, deepAgentCallbackController, knowledgeContextService, deepAgentConfig, null, null, null,
                new DeepRunEventHub());
    }

    /**
     * 无 Skill 上下文服务时退回 Agent 原生系统提示词的缺省上下文，保证单元测试链路可用。
     */
    private SkillRuntimeContext defaultSkillContext(AgentDefinition agent) {
        SkillRuntimeContext context = new SkillRuntimeContext();
        context.setSystemPrompt(StringUtils.defaultString(agent.getSystemPrompt()));
        context.setSnapshot("{\"installed\":false}");
        return context;
    }

    /**
     * 对话当前请求。
     */
    @ApiOperation("非流式聊天（兼容接口，已弃用；新调用请使用 /api/agent/chat/stream）")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse<AgentMessageVo> chat(@RequestBody AgentChatDto dto) {
        AgentDefinition agent = agentDefinitionService.getById(dto.getAgentId());
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.definition.not.found"));
        }
        if ("DEEP".equals(agent.getExecutionMode())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.chat.stream.required"));
        }
        return WebResponse.OK(agentChatService.chat(dto));
    }

    /**
     * 上传Attachments。
     */
    @ApiOperation("上传并识别聊天附件")
    @PostMapping(value = "/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WebResponse<List<AgentChatAttachmentVo>> uploadAttachments(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 3) {
            throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachments.max.exceeded"));
        }
        List<AgentChatAttachmentVo> attachments = files.stream().map(file -> {
            ChatAttachmentService.ChatAttachment attachment = chatAttachmentService.process(file);
            AgentChatAttachmentVo vo = new AgentChatAttachmentVo();
            vo.setFileName(attachment.getFileName());
            vo.setContentType(attachment.getContentType());
            vo.setSize(attachment.getSize());
            vo.setObjectKey(attachment.getObjectKey());
            vo.setExtractedContent(attachment.getExtractedContent());
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.OK(I18nUtils.getMessage("agent.chat.attachments.upload.success"), attachments);
    }

    /**
     * 流式聊天。
     */
    @ApiOperation("流式聊天")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentChatDto dto, HttpServletResponse response) {
        if (StringUtils.isBlank(dto.getRequestId())) {
            dto.setRequestId(UUID.randomUUID().toString());
        }
        AgentDefinition agent = agentChatService.getEnabledAgent(dto.getAgentId());
        if ("DEEP".equals(agent.getExecutionMode())) {
            if (StringUtils.isNotBlank(dto.getParentMessageId())) {
                return resumeDeep(dto, response, agent);
            }
            return streamDeep(dto, response, agent);
        }
        return openStream(dto, response);
    }

    /**
     * 与普通 Agent 共用 stream + parentMessageId 交互回复协议，恢复同一 Deep 运行。
     */
    private SseEmitter resumeDeep(AgentChatDto dto, HttpServletResponse response, AgentDefinition agent) {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) throw new ServerException(401, I18nUtils.getMessage("agent.unauthorized"));
        AgentConversation conversation = getDeepConversation(dto.getConversationId(), userId, agent);
        if (conversation == null)
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.tool.confirmation.conversation.required"));
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        SseEmitter emitter = new SseEmitter(deepStreamTimeoutMs());
        try {
            emitter.send(SseEmitter.event().comment("connected"));
            String runId = deepAgentRunService.resumeToolApproval(conversation.getId(), dto.getParentMessageId(), userId, dto.getAnswer());
            deepRunEventHub.add(runId, emitter);
            JSONObject accepted = new JSONObject();
            accepted.put("runId", runId);
            accepted.put("conversationId", conversation.getId());
            emitter.send(SseEmitter.event().name("accepted").data(accepted.toJSONString()));
        } catch (IllegalArgumentException e) {
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.resume.request.invalid"));
        } catch (Exception e) {
            throw new ServerException(502, I18nUtils.getMessage("agent.deep.resume.failed", new Object[]{e.getMessage()}));
        }
        return emitter;
    }

    /**
     * 处理openStream。
     */
    private SseEmitter openStream(AgentChatDto dto, HttpServletResponse response) {
        // 在主线程中提前获取userId，避免在线程池新线程中无法获取ThreadLocal中的用户信息
        String userId = CurrentUser.getUser() != null ? CurrentUser.getUser().get("userId") : null;
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, I18nUtils.getMessage("agent.unauthorized"));
        }
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<Future<?>> workerRef = new AtomicReference<>();
        Runnable cancelWorker = () -> {
            Future<?> worker = workerRef.get();
            if (worker != null && !worker.isDone()) worker.cancel(true);
        };
        emitter.onCompletion(() -> {
            closed.set(true);
            cancelWorker.run();
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            cancelWorker.run();
            emitter.complete();
        });
        emitter.onError(error -> {
            closed.set(true);
            cancelWorker.run();
        });

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            closed.set(true);
        }

        // 启动心跳，防止代理/负载均衡器因空闲断开连接
        ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                closed.set(true);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        final long enqueuedAt = System.currentTimeMillis();
        try {
            Future<?> worker = streamExecutor.submit(() -> {
                long queueWaitMs = System.currentTimeMillis() - enqueuedAt;
                if (queueWaitMs > 0) {
                    ChatLatencyMetrics.record("chat.stream.queue_wait", queueWaitMs);
                    log.info("流式聊天任务开始: queueWait={}ms, active={}, queued={}", queueWaitMs,
                            streamExecutor.getActiveCount(), streamExecutor.getQueue().size());
                }
                if (streamExecutor.getActiveCount() >= STREAM_ACTIVE_WARNING_THRESHOLD
                        || streamExecutor.getQueue().size() >= STREAM_QUEUE_WARNING_THRESHOLD) {
                    log.warn("流式聊天容量接近饱和: active={}, queued={}, poolSize={}",
                            streamExecutor.getActiveCount(), streamExecutor.getQueue().size(), streamExecutor.getPoolSize());
                }
                try {
                    dto.setUserId(userId);
                    MDC.put("chatRequestId", dto.getRequestId());
                    agentChatService.stream(dto, new SseAgentStreamCallback(emitter, closed, dto.getRequestId(), heartbeatScheduler));
                } catch (Exception e) {
                    log.error("流式聊天异常", e);
                    if (!closed.get()) {
                        try {
                            JSONObject errorData = new JSONObject();
                            errorData.put("code", 500);
                            errorData.put("message", I18nUtils.getMessage("agent.stream.failed"));
                            emitter.send(SseEmitter.event().name("error").data(errorData.toJSONString()));
                            closed.set(true);
                            emitter.complete();
                        } catch (IOException ignored) {
                            closed.set(true);
                        }
                    }
                } finally {
                    MDC.remove("chatRequestId");
                    heartbeatTask.cancel(false);
                }
            });
            workerRef.set(worker);
            if (closed.get()) {
                worker.cancel(true);
            }
        } catch (RejectedExecutionException e) {
            heartbeatTask.cancel(false);
            log.warn("流式聊天任务被拒绝: active={}, queued={}, completed={}", streamExecutor.getActiveCount(),
                    streamExecutor.getQueue().size(), streamExecutor.getCompletedTaskCount());
            if (!closed.get()) {
                try {
                    JSONObject errorData = new JSONObject();
                    errorData.put("code", 503);
                    errorData.put("message", "当前聊天请求较多，请稍后重试");
                    emitter.send(SseEmitter.event().name("error").data(errorData.toJSONString()));
                } catch (IOException | IllegalStateException ignored) {
                    // 客户端连接已关闭，无需额外处理。
                } finally {
                    closed.set(true);
                    emitter.complete();
                }
            }
        }
        return emitter;
    }

    /**
     * 会话列表。
     */
    @ApiOperation("会话列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/conversation/list")
    public WebResponse<List<AgentConversationVo>> list(@RequestBody ConversationList vo) {
        Page<AgentConversation> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        // 默认只显示开放会话（status=0），除非前端明确指定status参数
        Integer status = vo.getStatus() != null ? vo.getStatus() : 0;
        Wrapper<AgentConversation> wrapper = Wrappers.lambdaQuery(AgentConversation.class)
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentConversation::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(AgentConversation::getStatus, status)
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

    /**
     * 查询会话消息。
     */
    @ApiOperation("查询会话消息")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/conversation/{id}/messages")
    public WebResponse<List<AgentMessageVo>> messages(@PathVariable @NotBlank String id,
                                                      @RequestParam(defaultValue = "1") Long current,
                                                      @RequestParam(defaultValue = "20") Long pageSize) {
        AgentConversation conversation = agentConversationService.getOne(Wrappers.lambdaQuery(AgentConversation.class)
                .eq(AgentConversation::getId, id)
                .eq(AgentConversation::getDeleted, false)
                .eq(AgentConversation::getUserId, CurrentUser.getUser().get("userId")));
        if (conversation == null) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        Page<AgentMessage> page = new Page<>(current, pageSize);
        Wrapper<AgentMessage> wrapper = Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, id)
                .eq(AgentMessage::getDeleted, false)
                .orderByAsc(AgentMessage::getCreatedAt);
        Page<AgentMessage> result = agentMessageService.page(page, wrapper);
        List<AgentMessageVo> list = result.getRecords().stream().map(item -> {
            AgentMessageVo itemVo = new AgentMessageVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 表示Sse智能体Stream回调。
     */
    private static class SseAgentStreamCallback implements AgentStreamCallback {

        private static final long MESSAGE_COALESCE_MS = 40L;
        private static final int MESSAGE_COALESCE_CHARS = 256;

        private final SseEmitter emitter;
        private final AtomicBoolean closed;
        private final String requestId;
        private final ScheduledExecutorService scheduler;
        private final Object messageLock = new Object();
        private final StringBuilder pendingMessage = new StringBuilder();
        private ScheduledFuture<?> pendingMessageFlush;
        private boolean firstMessage = true;
        private String lastConversationId;

        /**
         * 创建 {@code SseAgentStreamCallback} 实例。
         */
        private SseAgentStreamCallback(SseEmitter emitter, AtomicBoolean closed, String requestId,
                                       ScheduledExecutorService scheduler) {
            this.emitter = emitter;
            this.closed = closed;
            this.requestId = requestId;
            this.scheduler = scheduler;
        }

        /**
         * 处理on消息。
         */
        @Override
        public boolean isCancelled() {
            return closed.get() || Thread.currentThread().isInterrupted();
        }

        @Override
        public void onMessage(String conversationId, String chunk) {
            if (StringUtils.isEmpty(chunk) || closed.get()) {
                return;
            }
            synchronized (messageLock) {
                lastConversationId = conversationId;
                if (firstMessage) {
                    firstMessage = false;
                    sendMessage(conversationId, chunk);
                    return;
                }
                pendingMessage.append(chunk);
                if (pendingMessage.length() >= MESSAGE_COALESCE_CHARS) {
                    flushPendingMessage(conversationId);
                } else if (pendingMessageFlush == null || pendingMessageFlush.isDone()) {
                    pendingMessageFlush = scheduler.schedule(() -> {
                        synchronized (messageLock) {
                            flushPendingMessage(conversationId);
                        }
                    }, MESSAGE_COALESCE_MS, TimeUnit.MILLISECONDS);
                }
            }
        }

        /**
         * 发送消息。
         */
        private void sendMessage(String conversationId, String chunk) {
            JSONObject data = new JSONObject();
            data.put("chunk", chunk);
            data.put("conversationId", conversationId);
            data.put("messageId", null);
            data.put("requestId", requestId);
            send("message", data, false);
        }

        /**
         * 处理flushPending消息。
         */
        private void flushPendingMessage(String conversationId) {
            if (pendingMessage.length() == 0) {
                return;
            }
            String chunk = pendingMessage.toString();
            pendingMessage.setLength(0);
            if (pendingMessageFlush != null) {
                pendingMessageFlush.cancel(false);
                pendingMessageFlush = null;
            }
            sendMessage(conversationId, chunk);
        }

        /**
         * 处理onReasoning。
         */
        @Override
        public void onReasoning(String conversationId, String chunk) {
            synchronized (messageLock) {
                flushPendingMessage(conversationId);
            }
            JSONObject data = new JSONObject();
            data.put("chunk", chunk);
            data.put("conversationId", conversationId);
            data.put("requestId", requestId);
            send("reasoning", data, false);
        }

        /**
         * 处理onToolCall。
         */
        @Override
        public void onToolCall(String conversationId, String toolCallJson) {
            synchronized (messageLock) {
                flushPendingMessage(conversationId);
            }
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("toolCalls", JSON.parseArray(toolCallJson));
            data.put("requestId", requestId);
            send("tool_call", data, false);
        }

        @Override
        public void onReplace(String conversationId, String content) {
            synchronized (messageLock) {
                pendingMessage.setLength(0);
            }
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("requestId", requestId);
            data.put("content", content);
            send("replace", data, false);
        }

        /**
         * 处理onQuestion。
         */
        @Override
        public void onQuestion(String conversationId, String runId, AgentMessageVo question) {
            synchronized (messageLock) {
                flushPendingMessage(conversationId);
            }
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("runId", runId);
            data.put("messageId", question.getId());
            data.put("content", question.getContent());
            data.put("messageType", question.getMessageType());
            data.put("interactionType", question.getInteractionType());
            data.put("interactionStatus", question.getInteractionStatus());
            data.put("questionConfig", JSON.parseObject(question.getQuestionConfig()));
            data.put("requestId", requestId);
            send("question", data, false);
        }

        /**
         * 处理onDone。
         */
        @Override
        public void onDone(String conversationId, String messageId, ModelStreamResponse response) {
            synchronized (messageLock) {
                flushPendingMessage(conversationId);
            }
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("messageId", messageId);
            data.put("requestId", requestId);
            if (response != null) {
                data.put("runId", response.getRunId());
                data.put("content", response.getContent());
                data.put("reasoningContent", response.getReasoningContent());
                data.put("model", response.getModel());
                data.put("promptTokens", response.getPromptTokens());
                data.put("completionTokens", response.getCompletionTokens());
                data.put("totalTokens", response.getTotalTokens());
                data.put("reasoningTokens", response.getReasoningTokens());
                data.put("cachedPromptTokens", response.getCachedPromptTokens());
                data.put("uncachedPromptTokens", response.getUncachedPromptTokens());
                data.put("promptCacheHitRate", response.getPromptCacheHitRate());
                data.put("waitingUser", response.getWaitingUser());
                data.put("sources", response.getSources());
            }
            send("done", data, true);
        }

        /**
         * 处理onError。
         */
        @Override
        public void onError(int code, String message) {
            synchronized (messageLock) {
                if (StringUtils.isNotBlank(lastConversationId)) {
                    flushPendingMessage(lastConversationId);
                } else {
                    if (pendingMessageFlush != null) {
                        pendingMessageFlush.cancel(false);
                        pendingMessageFlush = null;
                    }
                    pendingMessage.setLength(0);
                }
            }
            JSONObject data = new JSONObject();
            data.put("code", code);
            data.put("message", message);
            send("error", data, true);
        }

        /**
         * 判断是否为Closed。
         */
        @Override
        public boolean isClosed() {
            return closed.get();
        }

        /**
         * 处理on状态。
         */
        @Override
        public void onStatus(String stage, String message) {
            JSONObject data = new JSONObject();
            data.put("requestId", requestId);
            data.put("stage", stage);
            data.put("message", message);
            send("status", data, false);
        }

        /**
         * 发送当前请求。
         */
        private void send(String eventName, JSONObject data, boolean complete) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data.toJSONString()));
                if (complete) {
                    closed.set(true);
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                log.warn("SSE发送失败, event={}, error={}", eventName, e.getMessage());
                closed.set(true);
            }
        }
    }

    /**
     * 处理streamDeep。
     */
    private SseEmitter streamDeep(AgentChatDto dto, HttpServletResponse response, AgentDefinition agent) {
        String userId = CurrentUser.getUser() != null ? CurrentUser.getUser().get("userId") : null;
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, I18nUtils.getMessage("agent.unauthorized"));
        }
        AgentConversation existingConversation = getDeepConversation(dto.getConversationId(), userId, agent);

        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(deepStreamTimeoutMs());
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> heartbeatRef = new AtomicReference<>();
        AtomicReference<String> runIdRef = new AtomicReference<>();
        Runnable cleanup = () -> {
            closed.set(true);
            ScheduledFuture<?> heartbeat = heartbeatRef.get();
            if (heartbeat != null) heartbeat.cancel(false);
            String runId = runIdRef.get();
            if (runId != null) deepAgentCallbackController.removeCallback(runId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            closed.set(true);
        }

        heartbeatRef.set(heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                cleanup.run();
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS));
        if (closed.get()) cleanup.run();

        try {
            streamExecutor.execute(() -> {
                try {
                    AgentConversation conversation;
                    if (existingConversation != null) {
                        conversation = existingConversation;
                    } else {
                        conversation = new AgentConversation();
                        conversation.setUserId(userId);
                        conversation.setAgentDefinitionId(agent.getId());
                        conversation.setStatus(0);
                        conversation.setToolApprovalPolicy(normalizeToolApprovalPolicy(dto.getToolApprovalPolicy()));
                        agentConversationService.save(conversation);
                    }
                    final String conversationId = conversation.getId();

                    // Deep Agent 的模型由独立运行服务选择；不能在这里要求 Agent 配置普通聊天模型。
                    ModelProvider routingProvider = null;
                    SkillRuntimeContext skillContext = skillContextService == null ? defaultSkillContext(agent) : skillContextService.resolve(agent, dto, dto.getMessage(), routingProvider);
                    List<ModelChatMessage> ctx = new ArrayList<>();
                    if (StringUtils.isNotBlank(skillContext.getSystemPrompt()))
                        ctx.add(new ModelChatMessage("system", skillContext.getSystemPrompt()));
                    String taskContext = buildDeepTaskContext(dto);
                    List<Map<String, Object>> sources = knowledgeContextService.enhance(
                            ctx, userId, conversationId, agent.getId(), taskContext, skillContext.getKnowledgeBaseIds(),
                            dto.getRetrievalMode());

                    AgentStreamCallback callback = new AgentStreamCallback() {
                        /**
                         * 处理on消息。
                         */
                        @Override
                        public void onMessage(String cid, String chunk) {
                            if (closed.get()) return;
                            try {
                                JSONObject data = new JSONObject();
                                data.put("conversationId", cid);
                                data.put("chunk", chunk);
                                emitter.send(SseEmitter.event().name("message").data(data.toJSONString()));
                            } catch (IOException e) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 处理onReasoning。
                         */
                        @Override
                        public void onReasoning(String cid, String chunk) {
                            if (closed.get()) return;
                            try {
                                JSONObject data = new JSONObject();
                                data.put("conversationId", cid);
                                data.put("chunk", chunk);
                                emitter.send(SseEmitter.event().name("reasoning").data(data.toJSONString()));
                            } catch (IOException e) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 处理onToolCall。
                         */
                        @Override
                        public void onToolCall(String cid, String toolCallJson) {
                            if (closed.get()) return;
                            try {
                                JSONObject data = new JSONObject();
                                data.put("conversationId", cid);
                                data.put("toolCalls", JSON.parseArray(toolCallJson));
                                emitter.send(SseEmitter.event().name("tool_call").data(data.toJSONString()));
                            } catch (Exception e) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 处理on运行Step。
                         */
                        @Override
                        public void onRunStep(String runId, String stepJson) {
                            if (closed.get()) return;
                            try {
                                JSONObject step = JSON.parseObject(stepJson);
                                step.put("conversationId", conversationId);
                                emitter.send(SseEmitter.event().name("run_step").data(step.toJSONString()));
                            } catch (IOException e) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 处理onQuestion。
                         */
                        @Override
                        public void onQuestion(String cid, String rid, AgentMessageVo q) {
                            if (closed.get()) return;
                            try {
                                JSONObject question = new JSONObject();
                                question.put("conversationId", cid);
                                question.put("runId", rid);
                                question.put("messageId", q.getId());
                                question.put("content", q.getContent());
                                question.put("messageType", q.getMessageType());
                                question.put("interactionType", q.getInteractionType());
                                question.put("interactionStatus", q.getInteractionStatus());
                                question.put("questionConfig", JSON.parseObject(q.getQuestionConfig()));
                                emitter.send(SseEmitter.event().name("question").data(question.toJSONString()));
                            } catch (Exception e) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 处理onDone。
                         */
                        @Override
                        public void onDone(String callbackConversationId, String mid, ModelStreamResponse response) {
                            if (closed.get()) return;
                            try {
                                JSONObject done = new JSONObject();
                                done.put("conversationId", callbackConversationId);
                                done.put("messageId", mid);
                                done.put("runId", runIdRef.get());
                                if (response != null) {
                                    done.put("content", response.getContent());
                                    done.put("model", response.getModel());
                                    done.put("promptTokens", response.getPromptTokens());
                                    done.put("completionTokens", response.getCompletionTokens());
                                    done.put("totalTokens", response.getTotalTokens());
                                    done.put("cachedPromptTokens", response.getCachedPromptTokens());
                                    done.put("uncachedPromptTokens", response.getUncachedPromptTokens());
                                    done.put("promptCacheHitRate", response.getPromptCacheHitRate());
                                    done.put("sources", response.getSources());
                                }
                                emitter.send(SseEmitter.event().name("done").data(done.toJSONString()));
                                closed.set(true);
                                emitter.complete();
                            } catch (Exception ignored) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 处理onError。
                         */
                        @Override
                        public void onError(int code, String message) {
                            if (closed.get()) return;
                            try {
                                JSONObject err = new JSONObject();
                                err.put("code", code);
                                err.put("message", message);
                                emitter.send(SseEmitter.event().name("error").data(err.toJSONString()));
                                closed.set(true);
                                emitter.complete();
                            } catch (Exception ignored) {
                                closed.set(true);
                            }
                        }

                        /**
                         * 判断是否为Closed。
                         */
                        @Override
                        public boolean isClosed() {
                            return closed.get();
                        }
                    };
                    if (runtimeEmailCredentialStore != null && dto.getRuntimeSecrets() != null && !dto.getRuntimeSecrets().isEmpty()) {
                        runtimeEmailCredentialStore.putPending(conversationId, userId, dto.getRuntimeSecrets());
                    }
                    String runId = deepAgentRunService.startRun(agent, userId, conversationId, dto.getMessage(),
                            dto.getAttachmentContent(), dto.getAttachments(), sources, skillContext, registeredRunId -> {
                                runIdRef.set(registeredRunId);
                                deepAgentCallbackController.registerCallback(registeredRunId, callback);
                            });
                    runIdRef.set(runId);
                    JSONObject accepted = new JSONObject();
                    accepted.put("runId", runId);
                    accepted.put("conversationId", conversationId);
                    emitter.send(SseEmitter.event().name("accepted").data(accepted.toJSONString()));
                    deepAgentCallbackController.reconcileTerminalCallback(runId);
                } catch (Exception e) {
                    log.error("Deep Agent 流式启动失败", e);
                    if (!closed.get()) {
                        try {
                            JSONObject err = new JSONObject();
                            err.put("code", 500);
                            err.put("message", "Deep Agent 启动失败: " + e.getMessage());
                            emitter.send(SseEmitter.event().name("error").data(err.toJSONString()));
                            closed.set(true);
                            emitter.complete();
                        } catch (IOException ignored) {
                        }
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Deep Agent 流式任务被拒绝: active={}, queued={}", streamExecutor.getActiveCount(),
                    streamExecutor.getQueue().size());
            if (!closed.get()) {
                try {
                    JSONObject err = new JSONObject();
                    err.put("code", 503);
                    err.put("message", "当前聊天请求较多，请稍后重试");
                    emitter.send(SseEmitter.event().name("error").data(err.toJSONString()));
                } catch (IOException | IllegalStateException ignored) {
                    // 客户端连接已关闭，无需额外处理。
                } finally {
                    cleanup.run();
                    emitter.complete();
                }
            }
        }
        return emitter;
    }

    /**
     * 附件正文只作为执行上下文使用，聊天记录仍保留用户原始提问及附件元数据。
     */
    private String buildDeepTaskContext(AgentChatDto dto) {
        if (StringUtils.isBlank(dto.getAttachmentContent())) {
            return dto.getMessage();
        }
        return StringUtils.defaultString(dto.getMessage()) + "\n\n附件内容：\n" + dto.getAttachmentContent();
    }

    /**
     * 获取Deep会话。
     */
    private AgentConversation getDeepConversation(String conversationId, String userId, AgentDefinition agent) {
        if (StringUtils.isBlank(conversationId)) {
            return null;
        }
        AgentConversation conversation = agentConversationService.getById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new ServerException(403, I18nUtils.getMessage("agent.conversation.access.denied"));
        }
        if (!agent.getId().equals(conversation.getAgentDefinitionId())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.conversation.agent.mismatch"));
        }
        if (!Integer.valueOf(0).equals(conversation.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.conversation.closed"));
        }
        return conversation;
    }

    /**
     * 处理deepStreamTimeoutMs。
     */
    private long deepStreamTimeoutMs() {
        long runTimeoutSeconds = deepAgentConfig.getRunTimeoutSeconds();
        if (runTimeoutSeconds <= 0) {
            runTimeoutSeconds = DEFAULT_DEEP_RUN_TIMEOUT_SECONDS;
        }
        long totalSeconds;
        if (runTimeoutSeconds > Long.MAX_VALUE - DEEP_STREAM_TIMEOUT_MARGIN_SECONDS) {
            totalSeconds = Long.MAX_VALUE;
        } else {
            totalSeconds = runTimeoutSeconds + DEEP_STREAM_TIMEOUT_MARGIN_SECONDS;
        }
        return totalSeconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : totalSeconds * 1000L;
    }

    /**
     * 规范化ToolApprovalPolicy。
     */
    private String normalizeToolApprovalPolicy(String policy) {
        return "risky".equals(policy) || "never".equals(policy) ? policy : "ask";
    }

}
