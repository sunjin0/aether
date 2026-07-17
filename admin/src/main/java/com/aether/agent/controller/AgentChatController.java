package com.aether.agent.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.vo.AgentConversationVo;
import com.aether.agent.vo.AgentMessageVo;
import com.alibaba.fastjson2.JSON;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
    private static final long HEARTBEAT_INTERVAL_MS = 15000L; // 15秒心跳

    private final AgentChatService agentChatService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    public AgentChatController(AgentChatService agentChatService, AgentConversationService agentConversationService, AgentMessageService agentMessageService) {
        this.agentChatService = agentChatService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
    }

    @ApiOperation("非流式聊天")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping
    public WebResponse<AgentMessageVo> chat(@RequestBody AgentChatDto dto) {
        return WebResponse.OK(agentChatService.chat(dto));
    }

    @ApiOperation("流式聊天")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentChatDto dto, HttpServletResponse response) {
        // 在主线程中提前获取userId，避免在线程池新线程中无法获取ThreadLocal中的用户信息
        String userId = CurrentUser.getUser() != null ? CurrentUser.getUser().get("userId") : null;
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, "未授权");
        }
        dto.setUserId(userId);

        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(error -> closed.set(true));

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

        streamExecutor.execute(() -> {
            try {
                agentChatService.stream(dto, new SseAgentStreamCallback(emitter, closed));
            } catch (Exception e) {
                log.error("流式聊天异常", e);
                if (!closed.get()) {
                    try {
                        JSONObject errorData = new JSONObject();
                        errorData.put("code", 500);
                        errorData.put("message", "服务内部错误");
                        emitter.send(SseEmitter.event().name("error").data(errorData.toJSONString()));
                        closed.set(true);
                        emitter.complete();
                    } catch (IOException ignored) {
                        closed.set(true);
                    }
                }
            } finally {
                heartbeatTask.cancel(false);
            }
        });
        return emitter;
    }

    @ApiOperation("会话列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/conversation/list")
    public WebResponse<List<AgentConversationVo>> list(@RequestBody AgentConversationVo vo) {
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
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
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

    private static class SseAgentStreamCallback implements AgentStreamCallback {

        private final SseEmitter emitter;
        private final AtomicBoolean closed;

        private SseAgentStreamCallback(SseEmitter emitter, AtomicBoolean closed) {
            this.emitter = emitter;
            this.closed = closed;
        }

        @Override
        public void onMessage(String conversationId, String chunk) {
            JSONObject data = new JSONObject();
            data.put("chunk", chunk);
            data.put("conversationId", conversationId);
            data.put("messageId", null);
            send("message", data, false);
        }

        @Override
        public void onReasoning(String conversationId, String chunk) {
            JSONObject data = new JSONObject();
            data.put("chunk", chunk);
            data.put("conversationId", conversationId);
            send("reasoning", data, false);
        }

        @Override
        public void onToolCall(String conversationId, String toolCallJson) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("toolCalls", JSON.parseArray(toolCallJson));
            send("tool_call", data, false);
        }

        @Override
        public void onQuestion(String conversationId, String runId, AgentMessageVo question) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("runId", runId);
            data.put("messageId", question.getId());
            data.put("content", question.getContent());
            data.put("messageType", question.getMessageType());
            data.put("interactionType", question.getInteractionType());
            data.put("interactionStatus", question.getInteractionStatus());
            data.put("questionConfig", JSON.parseObject(question.getQuestionConfig()));
            send("question", data, false);
        }

        @Override
        public void onDone(String conversationId, String messageId, ModelStreamResponse response) {
            JSONObject data = new JSONObject();
            data.put("conversationId", conversationId);
            data.put("messageId", messageId);
            if (response != null) {
                data.put("content", response.getContent());
                data.put("reasoningContent", response.getReasoningContent());
                data.put("model", response.getModel());
                data.put("promptTokens", response.getPromptTokens());
                data.put("completionTokens", response.getCompletionTokens());
                data.put("totalTokens", response.getTotalTokens());
                data.put("reasoningTokens", response.getReasoningTokens());
                data.put("waitingUser", response.getWaitingUser());
                data.put("sources", response.getSources());
            }
            send("done", data, true);
        }

        @Override
        public void onError(int code, String message) {
            JSONObject data = new JSONObject();
            data.put("code", code);
            data.put("message", message);
            send("error", data, true);
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

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

}
