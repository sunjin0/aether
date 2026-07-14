package com.aether.agent.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.executor.ToolExecutorFactory;
import com.aether.agent.tools.entity.ToolResult;
import com.aether.agent.tools.core.Tool;
import com.aether.agent.tools.core.ToolRegistry;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelStreamCallback;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Agent聊天服务实现。
 */
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatServiceImpl.class);
    private static final int AGENT_STATUS_ENABLED = 1;
    private static final int PROVIDER_STATUS_ENABLED = 1;
    private static final int CONVERSATION_STATUS_OPEN = 0;
    private static final int RUN_STATUS_SUCCESS = 0;
    private static final int RUN_STATUS_FAILED = 1;
    private static final int RUN_STATUS_WAITING_USER = 3;
    private static final String ASK_USER_TOOL_NAME = "ask_user";
    private static final String MESSAGE_TYPE_CHAT = "chat";
    private static final String MESSAGE_TYPE_INTERACTION = "interaction";
    private static final String MESSAGE_TYPE_ANSWER = "answer";
    private static final String INTERACTION_STATUS_PENDING = "pending";
    private static final String INTERACTION_STATUS_ANSWERED = "answered";
    private static final String INTERACTIVE_QUESTION_POLICY = "# ask_user 工具调用规范\n" +
            "\n" +
            "## 触发条件（三选一）\n" +
            "- 对用户意图有疑问 → 需澄清\n" +
            "- 已有多套可行方案 → 需用户选择\n" +
            "- 操作前需用户授权 → 需确认\n" +
            "\n" +
            "## 执行规则（强制）\n" +
            "1. **禁止文本提问**：所有问题必须通过 ask_user 提出，MUST NOT 以普通回复形式提问。\n" +
            "2. **批量提问**：多个问题必须一次性放入 questions 数组，前端分页展示。\n" +
            "3. **问题格式**：每个问题必须包含 id（snake_case）和 type（choice/confirm）。choice 必须带 options 列表。\n" +
            "4. **禁止动态表单**：不支持 form；若需自由文本输入，直接输出普通追问（不调用工具）。\n" +
            "5. **退出条件**：若无问题需要用户输入，则输出普通助手回复，禁止调用工具。";
    private static final int TOOL_CALL_STATUS_SUCCESS = 0;
    private static final int TOOL_CALL_STATUS_FAILED = 1;
    private static final int TOOL_CALL_STATUS_TIMEOUT = 2;
    private static final int TOOL_CALL_STATUS_SECURITY_BLOCK = 3;
    private static final int MAX_TOOL_CALL_ITERATIONS = 5; // 最大工具调用迭代次数
    private static final String CONTEXT_CACHE_KEY_PREFIX = "agent:context:";
    private static final long CONTEXT_CACHE_TTL_MINUTES = 30;
    private static final String SUMMARY_CACHE_KEY_PREFIX = "agent:summary:";
    private static final long SUMMARY_CACHE_TTL_HOURS = 24;
    private static final int SUMMARY_TRIGGER_THRESHOLD = 10; // 超过10轮对话触发摘要
    private static final int KEEP_RECENT_MESSAGES = 5; // 保留最近5条完整消息
    private static final int SUMMARY_OLD_MESSAGE_LIMIT = 30; // 摘要最多取30条旧消息
    private static final String TOOLS_CACHE_KEY_PREFIX = "agent:tools:";
    private static final long TOOLS_CACHE_TTL_MINUTES = 10;

    private final AgentDefinitionService agentDefinitionService;
    private final ModelProviderService modelProviderService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final AgentRunService agentRunService;
    private final ModelClientFactory modelClientFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentToolService agentToolService;
    private final AgentToolBindingService agentToolBindingService;
    private final AgentToolCallLogService agentToolCallLogService;
    private final ToolExecutorFactory toolExecutorFactory;
    private final ToolRegistry toolRegistry;
    private final ExecutorService summaryExecutor = Executors.newFixedThreadPool(2);

    public AgentChatServiceImpl(AgentDefinitionService agentDefinitionService,
                                ModelProviderService modelProviderService,
                                AgentConversationService agentConversationService,
                                AgentMessageService agentMessageService,
                                AgentRunService agentRunService,
                                ModelClientFactory modelClientFactory,
                                RedisTemplate<String, Object> redisTemplate,
                                AgentToolService agentToolService,
                                 AgentToolBindingService agentToolBindingService,
                                 AgentToolCallLogService agentToolCallLogService,
                                 ToolExecutorFactory toolExecutorFactory,
                                 ToolRegistry toolRegistry) {
        this.agentDefinitionService = agentDefinitionService;
        this.modelProviderService = modelProviderService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.agentRunService = agentRunService;
        this.modelClientFactory = modelClientFactory;
        this.redisTemplate = redisTemplate;
        this.agentToolService = agentToolService;
        this.agentToolBindingService = agentToolBindingService;
        this.agentToolCallLogService = agentToolCallLogService;
        this.toolExecutorFactory = toolExecutorFactory;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public AgentMessageVo chat(AgentChatDto dto) {
        validateRequest(dto);
        String userId = getCurrentUserId(dto);
        long startTime = System.currentTimeMillis();

        AgentDefinition agent = getEnabledAgent(dto.getAgentId());
        ModelProvider provider = getEnabledProvider(agent.getModelProviderId());
        applyThinkingConfig(dto, agent);
        AgentConversation conversation = getOrCreateConversation(dto, userId, agent);
        AgentMessage userMessage = saveUserMessage(conversation.getId(), dto.getMessage());
        String runId = null;

        try {
            List<ModelChatMessage> context = buildContextWithSummary(agent, provider, conversation.getId());
            applyInteractiveQuestionPolicy(context);
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);
            request.setTools(getRequestTools(agent.getId()));

            ModelClient modelClient = modelClientFactory.getClient(provider);
            ModelChatResponse modelResponse = modelClient.chat(request);
            modelResponse = retryAskUserWhenPlainQuestion(modelResponse, modelClient, request);
            
            // 推理未开启时，过滤掉模型可能返回的reasoning_content和reasoning_tokens
            if (!Boolean.TRUE.equals(agent.getDefaultThinking())) {
                modelResponse.setReasoningContent(null);
                modelResponse.setReasoningTokens(null);
            }
            
            // 处理工具调用
            int iteration = 0;
            boolean toolCallAttempted = false;
            boolean toolCallSucceeded = false;
            while (hasToolCalls(modelResponse) && iteration < MAX_TOOL_CALL_ITERATIONS) {
                if (runId == null) {
                    runId = saveRun(agent, provider, userId, conversation.getId(), userMessage.getId(), dto.getMessage(), modelResponse, 0, RUN_STATUS_SUCCESS, null);
                }
                iteration++;
                toolCallAttempted = true;
                ToolResult internalToolResult = handleInternalToolCall(conversation.getId(), modelResponse, System.currentTimeMillis() - startTime);
                if (internalToolResult != null) {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    AgentMessage questionMessage = internalToolResult.getMessage();
                    updateConversationMessageCount(conversation.getId());
                    boolean waitingUser = MESSAGE_TYPE_INTERACTION.equals(questionMessage.getMessageType());
                    updateRun(runId, questionMessage.getId(), modelResponse, latencyMs,
                            waitingUser ? RUN_STATUS_WAITING_USER : RUN_STATUS_SUCCESS, null);
                    AgentMessageVo vo = new AgentMessageVo();
                    BeanUtils.copyProperties(questionMessage, vo);
                    return vo;
                }
                List<ToolExecutionResult> toolResults = executeToolCalls(modelResponse, agent, userId, runId);
                toolCallSucceeded = toolCallSucceeded || hasSuccessfulToolResult(toolResults);
                
                addToolResultsToContext(context, modelResponse, toolResults);
                
                // 继续调用模型
                request.setMessages(context);
                modelResponse = modelClient.chat(request);
            }
            
            long latencyMs = System.currentTimeMillis() - startTime;
            ToolAuthenticityCheck authenticityCheck = checkToolAuthenticity(modelResponse.getContent(), toolCallAttempted, toolCallSucceeded);
            if (!authenticityCheck.isValid()) {
                log.warn("拦截疑似工具结果幻觉回答: conversationId={}, attempted={}, succeeded={}, reason={}",
                        conversation.getId(), toolCallAttempted, toolCallSucceeded, authenticityCheck.getReason());
                modelResponse.setContent(buildToolAuthenticityFallback(authenticityCheck));
            }

            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs);
            updateConversationMessageCount(conversation.getId());
            if (runId == null) {
                runId = saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), dto.getMessage(), modelResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            } else {
                updateRun(runId, assistantMessage.getId(), modelResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            }

            AgentMessageVo vo = new AgentMessageVo();
            BeanUtils.copyProperties(assistantMessage, vo);
            return vo;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (runId == null) {
                saveFailedRun(agent, provider, userId, conversation.getId(), userMessage.getId(), dto.getMessage(), latencyMs, e);
            } else {
                updateRun(runId, userMessage.getId(), null, latencyMs, RUN_STATUS_FAILED, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public void stream(AgentChatDto dto, AgentStreamCallback callback) {
        validateStreamRequest(dto);
        if (isInteractionReplyRequest(dto)) {
            streamReply(dto, callback);
            return;
        }
        String userId = getCurrentUserId(dto);
        long startTime = System.currentTimeMillis();

        AgentDefinition agent = getEnabledAgent(dto.getAgentId());
        ModelProvider provider = getEnabledProvider(agent.getModelProviderId());
        applyThinkingConfig(dto, agent);
        AgentConversation conversation = getOrCreateConversation(dto, userId, agent);
        AgentMessage userMessage = saveUserMessage(conversation.getId(), dto.getMessage());
        String runId = null;

        log.info("流式请求开始: agent={}, model={}, thinking={}", agent.getId(), agent.getModel(), agent.getDefaultThinking());

        try {
            long t0 = System.currentTimeMillis();
            List<ModelChatMessage> context = buildContextWithSummary(agent, provider, conversation.getId());
            applyInteractiveQuestionPolicy(context);
            long t1 = System.currentTimeMillis();
            log.info("上下文构建耗时: {}ms", t1 - t0);

            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);
            request.setTools(getRequestTools(agent.getId()));

            ModelClient modelClient = modelClientFactory.getClient(provider);
            // 推理未开启时，不转发reasoning chunks
            boolean thinkingEnabled = Boolean.TRUE.equals(agent.getDefaultThinking());
            ModelStreamResponse modelResponse = modelClient.stream(request, new ModelStreamCallback() {
                @Override
                public void onMessage(String chunk) {
                    if (!callback.isClosed()) {
                        callback.onMessage(conversation.getId(), chunk);
                    }
                }

                @Override
                public void onReasoning(String chunk) {
                    if (thinkingEnabled && !callback.isClosed()) {
                        callback.onReasoning(conversation.getId(), chunk);
                    }
                }

                @Override
                public void onToolCall(String toolCallJson) {
                    if (!callback.isClosed()) {
                        callback.onToolCall(conversation.getId(), toolCallJson);
                    }
                }

                @Override
                public boolean isClosed() {
                    return callback.isClosed();
                }
            });
            
            // 推理未开启时，过滤掉模型可能返回的reasoning_content和reasoning_tokens
            if (!thinkingEnabled) {
                modelResponse.setReasoningContent(null);
                modelResponse.setReasoningTokens(null);
            }
            validateNonEmptyStreamResponse(modelResponse);
            
            // 当模型未提供token统计时（如Google Gemma流式响应），补充估算值
            fillDefaultTokens(modelResponse, context, dto.getMessage());
            
            // 打印完整模型响应（调试用）
            log.info("模型完整响应: content={}, toolCalls={}, reasoningContent={}, model={}, tokens={}/{}",
                    modelResponse.getContent(),
                    modelResponse.getToolCalls(),
                    modelResponse.getReasoningContent(),
                    modelResponse.getModel(),
                    modelResponse.getPromptTokens(),
                    modelResponse.getCompletionTokens());
            
            log.info("流式请求完成: 总耗时={}ms", System.currentTimeMillis() - startTime);
            
            // 处理工具调用循环
            int iteration = 0;
            boolean toolCallAttempted = false;
            boolean toolCallSucceeded = false;
            ModelChatResponse chatResponse = toChatResponse(modelResponse);
            while (hasToolCalls(chatResponse) && iteration < MAX_TOOL_CALL_ITERATIONS) {
                if (runId == null) {
                    runId = saveRun(agent, provider, userId, conversation.getId(), userMessage.getId(), dto.getMessage(), chatResponse, 0, RUN_STATUS_SUCCESS, null);
                }
                iteration++;
                toolCallAttempted = true;
                AgentMessage assistantPrelude = hasInternalToolCall(chatResponse)
                        ? saveAssistantPreludeIfPresent(conversation.getId(), chatResponse, System.currentTimeMillis() - startTime)
                        : null;
                ToolResult internalToolResult = handleInternalToolCall(conversation.getId(), chatResponse, System.currentTimeMillis() - startTime);
                if (internalToolResult != null) {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    AgentMessage questionMessage = internalToolResult.getMessage();
                    updateConversationMessageCount(conversation.getId());
                    AgentMessageVo questionVo = new AgentMessageVo();
                    BeanUtils.copyProperties(questionMessage, questionVo);

                    boolean waitingUser = MESSAGE_TYPE_INTERACTION.equals(questionMessage.getMessageType());
                    updateRun(runId, questionMessage.getId(), chatResponse, latencyMs,
                            waitingUser ? RUN_STATUS_WAITING_USER : RUN_STATUS_SUCCESS, null);
                    
                    if (!callback.isClosed()) {
                        ModelStreamResponse doneResponse = new ModelStreamResponse();
                        AgentMessage doneMessage = assistantPrelude != null ? assistantPrelude : questionMessage;
                        doneResponse.setContent(doneMessage.getContent());
                        doneResponse.setWaitingUser(waitingUser);
                        if (assistantPrelude != null) {
                            callback.onDone(conversation.getId(), assistantPrelude.getId(), doneResponse);
                        }
                        callback.onQuestion(conversation.getId(), runId, questionVo);
                        if (assistantPrelude == null) {
                            callback.onDone(conversation.getId(), questionMessage.getId(), doneResponse);
                        }
                    }
                    return;
                }
                List<ToolExecutionResult> toolResults = executeToolCalls(chatResponse, agent, userId, runId);
                toolCallSucceeded = toolCallSucceeded || hasSuccessfulToolResult(toolResults);
                
                addToolResultsToContext(context, chatResponse, toolResults);
                
                // 继续调用模型（流式）
                request.setMessages(context);
                modelResponse = modelClient.stream(request, new ModelStreamCallback() {
                    @Override
                    public void onMessage(String chunk) {
                        if (!callback.isClosed()) {
                            callback.onMessage(conversation.getId(), chunk);
                        }
                    }

                    @Override
                    public void onReasoning(String chunk) {
                        if (thinkingEnabled && !callback.isClosed()) {
                            callback.onReasoning(conversation.getId(), chunk);
                        }
                    }

                    @Override
                    public void onToolCall(String toolCallJson) {
                        if (!callback.isClosed()) {
                            callback.onToolCall(conversation.getId(), toolCallJson);
                        }
                    }

                    @Override
                    public boolean isClosed() {
                        return callback.isClosed();
                    }
                });
                
                // 推理未开启时，过滤掉reasoning_content
                if (!thinkingEnabled) {
                    modelResponse.setReasoningContent(null);
                }
                validateNonEmptyStreamResponse(modelResponse);
                
                fillDefaultTokens(modelResponse, context, dto.getMessage());
                chatResponse = toChatResponse(modelResponse);
            }
            
            long latencyMs = System.currentTimeMillis() - startTime;
            ToolAuthenticityCheck authenticityCheck = checkToolAuthenticity(modelResponse.getContent(), toolCallAttempted, toolCallSucceeded);
            if (!authenticityCheck.isValid()) {
                log.warn("拦截疑似工具结果幻觉流式回答: conversationId={}, attempted={}, succeeded={}, reason={}",
                        conversation.getId(), toolCallAttempted, toolCallSucceeded, authenticityCheck.getReason());
                modelResponse.setContent(buildToolAuthenticityFallback(authenticityCheck));
                chatResponse.setContent(modelResponse.getContent());
            }

            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs);
            updateConversationMessageCount(conversation.getId());
            if (runId == null) {
                runId = saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), dto.getMessage(), chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            } else {
                updateRun(runId, assistantMessage.getId(), chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            }
            if (!callback.isClosed()) {
                callback.onDone(conversation.getId(), assistantMessage.getId(), modelResponse);
            }
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (runId == null) {
                saveFailedRun(agent, provider, userId, conversation.getId(), userMessage.getId(), dto.getMessage(), latencyMs, e);
            } else {
                updateRun(runId, userMessage.getId(), null, latencyMs, RUN_STATUS_FAILED, e.getMessage());
            }
            if (!callback.isClosed()) {
                callback.onError(resolveErrorCode(e), resolveErrorMessage(e));
            }
        }
    }

    private void streamReply(AgentChatDto dto, AgentStreamCallback callback) {
        String userId = resolveUserId(dto.getUserId());
        long startTime = System.currentTimeMillis();
        String runId = null;
        AgentConversation conversation = null;
        AgentDefinition agent = null;
        ModelProvider provider = null;
        AgentMessage answerMessage = null;
        String answerContent = null;

        try {
            conversation = agentConversationService.getById(dto.getConversationId());
            if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())) {
                throw new ServerException(404, "会话不存在");
            }
            if (!userId.equals(conversation.getUserId())) {
                throw new ServerException(403, "无权访问会话");
            }
            if (!Integer.valueOf(CONVERSATION_STATUS_OPEN).equals(conversation.getStatus())) {
                throw new ServerException(422, "会话已关闭");
            }

            AgentMessage question = agentMessageService.getOne(Wrappers.lambdaQuery(AgentMessage.class)
                    .eq(AgentMessage::getId, dto.getParentMessageId())
                    .eq(AgentMessage::getConversationId, conversation.getId())
                    .eq(AgentMessage::getDeleted, false));
            if (question == null || !MESSAGE_TYPE_INTERACTION.equals(question.getMessageType())) {
                throw new ServerException(404, "提问消息不存在");
            }
            if (!INTERACTION_STATUS_PENDING.equals(question.getInteractionStatus())) {
                throw new ServerException(409, "提问已处理");
            }
            if (question.getExpiresAt() != null && question.getExpiresAt() < System.currentTimeMillis()) {
                markInteractionStatus(question.getId(), "expired", null);
                throw new ServerException(409, "提问已过期");
            }

            answerContent = renderAnswerContent(question, dto.getAnswer());
            answerMessage = saveAnswerMessage(conversation.getId(), question.getId(), answerContent);
            markInteractionAnswered(question, dto.getAnswer(), System.currentTimeMillis());

            agent = getEnabledAgent(conversation.getAgentDefinitionId());
            provider = getEnabledProvider(agent.getModelProviderId());
            applyReplyThinkingConfig(dto, agent);
            boolean thinkingEnabled = Boolean.TRUE.equals(agent.getDefaultThinking());

            List<ModelChatMessage> context = buildContextWithSummary(agent, provider, conversation.getId());
            applyInteractiveQuestionPolicy(context);
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);
            request.setTools(getRequestTools(agent.getId()));

            ModelClient modelClient = modelClientFactory.getClient(provider);
            ModelStreamResponse modelResponse = modelClient.stream(request, new ModelStreamCallback() {
                @Override
                public void onMessage(String chunk) {
                    if (!callback.isClosed()) {
                        callback.onMessage(dto.getConversationId(), chunk);
                    }
                }

                @Override
                public void onReasoning(String chunk) {
                    if (thinkingEnabled && !callback.isClosed()) {
                        callback.onReasoning(dto.getConversationId(), chunk);
                    }
                }

                @Override
                public void onToolCall(String toolCallJson) {
                    if (!callback.isClosed()) {
                        callback.onToolCall(dto.getConversationId(), toolCallJson);
                    }
                }

                @Override
                public boolean isClosed() {
                    return callback.isClosed();
                }
            });

            if (!thinkingEnabled) {
                modelResponse.setReasoningContent(null);
                modelResponse.setReasoningTokens(null);
            }
            validateNonEmptyStreamResponse(modelResponse);
            fillDefaultTokens(modelResponse, context, answerContent);

            int iteration = 0;
            boolean toolCallAttempted = false;
            boolean toolCallSucceeded = false;
            ModelChatResponse chatResponse = toChatResponse(modelResponse);
            while (hasToolCalls(chatResponse) && iteration < MAX_TOOL_CALL_ITERATIONS) {
                if (runId == null) {
                    runId = saveRun(agent, provider, userId, conversation.getId(), answerMessage.getId(), answerContent, chatResponse, 0, RUN_STATUS_SUCCESS, null);
                }
                iteration++;
                toolCallAttempted = true;
                AgentMessage assistantPrelude = hasInternalToolCall(chatResponse)
                        ? saveAssistantPreludeIfPresent(conversation.getId(), chatResponse, System.currentTimeMillis() - startTime)
                        : null;
                ToolResult internalToolResult = handleInternalToolCall(conversation.getId(), chatResponse, System.currentTimeMillis() - startTime);
                if (internalToolResult != null) {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    AgentMessage nextQuestion = internalToolResult.getMessage();
                    updateConversationMessageCount(conversation.getId());

                    AgentMessageVo questionVo = new AgentMessageVo();
                    BeanUtils.copyProperties(nextQuestion, questionVo);

                    boolean waitingUser = MESSAGE_TYPE_INTERACTION.equals(nextQuestion.getMessageType());
                    updateRun(runId, nextQuestion.getId(), chatResponse, latencyMs,
                            waitingUser ? RUN_STATUS_WAITING_USER : RUN_STATUS_SUCCESS, null);

                    if (!callback.isClosed()) {
                        ModelStreamResponse doneResponse = new ModelStreamResponse();
                        AgentMessage doneMessage = assistantPrelude != null ? assistantPrelude : nextQuestion;
                        doneResponse.setContent(doneMessage.getContent());
                        doneResponse.setWaitingUser(waitingUser);
                        if (assistantPrelude != null) {
                            callback.onDone(conversation.getId(), assistantPrelude.getId(), doneResponse);
                        }
                        callback.onQuestion(conversation.getId(), runId, questionVo);
                        if (assistantPrelude == null) {
                            callback.onDone(conversation.getId(), nextQuestion.getId(), doneResponse);
                        }
                    }
                    return;
                }

                List<ToolExecutionResult> toolResults = executeToolCalls(chatResponse, agent, userId, runId);
                toolCallSucceeded = toolCallSucceeded || hasSuccessfulToolResult(toolResults);
                addToolResultsToContext(context, chatResponse, toolResults);

                request.setMessages(context);
                modelResponse = modelClient.stream(request, new ModelStreamCallback() {
                    @Override
                    public void onMessage(String chunk) {
                        if (!callback.isClosed()) {
                            callback.onMessage(dto.getConversationId(), chunk);
                        }
                    }

                    @Override
                    public void onReasoning(String chunk) {
                        if (thinkingEnabled && !callback.isClosed()) {
                            callback.onReasoning(dto.getConversationId(), chunk);
                        }
                    }

                    @Override
                    public void onToolCall(String toolCallJson) {
                        if (!callback.isClosed()) {
                            callback.onToolCall(dto.getConversationId(), toolCallJson);
                        }
                    }

                    @Override
                    public boolean isClosed() {
                        return callback.isClosed();
                    }
                });
                if (!thinkingEnabled) {
                    modelResponse.setReasoningContent(null);
                    modelResponse.setReasoningTokens(null);
                }
                validateNonEmptyStreamResponse(modelResponse);
                fillDefaultTokens(modelResponse, context, answerContent);
                chatResponse = toChatResponse(modelResponse);
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            ToolAuthenticityCheck authenticityCheck = checkToolAuthenticity(modelResponse.getContent(), toolCallAttempted, toolCallSucceeded);
            if (!authenticityCheck.isValid()) {
                modelResponse.setContent(buildToolAuthenticityFallback(authenticityCheck));
                chatResponse.setContent(modelResponse.getContent());
            }

            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs);
            updateConversationMessageCount(conversation.getId());
            if (runId == null) {
                saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), answerContent, chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            } else {
                updateRun(runId, assistantMessage.getId(), chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            }
            if (!callback.isClosed()) {
                callback.onDone(conversation.getId(), assistantMessage.getId(), modelResponse);
            }
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (agent != null && provider != null && conversation != null && answerMessage != null) {
                if (runId == null) {
                    saveFailedRun(agent, provider, userId, conversation.getId(), answerMessage.getId(), answerContent, latencyMs, e);
                } else {
                    updateRun(runId, answerMessage.getId(), null, latencyMs, RUN_STATUS_FAILED, e.getMessage());
                }
            }
            if (!callback.isClosed()) {
                callback.onError(resolveErrorCode(e), resolveErrorMessage(e));
            }
        }
    }

    private void validateRequest(AgentChatDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getAgentId()) || StringUtils.isBlank(dto.getMessage())) {
            throw new ServerException(400, "参数错误");
        }
    }

    private void validateStreamRequest(AgentChatDto dto) {
        if (dto == null) {
            throw new ServerException(400, "参数错误");
        }
        if (isInteractionReplyRequest(dto)) {
            if (StringUtils.isNotBlank(dto.getConversationId())
                    && StringUtils.isNotBlank(dto.getParentMessageId())
                    && dto.getAnswer() != null) {
                return;
            }
            throw new ServerException(400, "参数错误");
        }
        validateRequest(dto);
    }

    private boolean isInteractionReplyRequest(AgentChatDto dto) {
        return dto != null && (
            StringUtils.isNotBlank(dto.getParentMessageId())
            || dto.getAnswer() != null
        );
    }

    /**
     * 将DTO中的深度思考配置合并到Agent定义上（DTO优先级高于Agent默认值）。
     * 默认关闭推理，仅当用户显式传入thinking=true时才开启。
     */
    private void applyThinkingConfig(AgentChatDto dto, AgentDefinition agent) {
        // 默认关闭推理，除非用户显式开启
        agent.setDefaultThinking(Boolean.TRUE.equals(dto.getThinking()));
        if (StringUtils.isNotBlank(dto.getReasoningEffort())) {
            String effort = dto.getReasoningEffort().toLowerCase();
            if (!effort.equals("low") && !effort.equals("medium") && !effort.equals("high")) {
                throw new ServerException(400, "reasoningEffort必须为low/medium/high");
            }
            agent.setDefaultReasoningEffort(effort);
        }
    }

    private void applyReplyThinkingConfig(AgentChatDto dto, AgentDefinition agent) {
        agent.setDefaultThinking(Boolean.TRUE.equals(dto.getThinking()));
        if (StringUtils.isNotBlank(dto.getReasoningEffort())) {
            String effort = dto.getReasoningEffort().toLowerCase();
            if (!effort.equals("low") && !effort.equals("medium") && !effort.equals("high")) {
                throw new ServerException(400, "reasoningEffort必须为low/medium/high");
            }
            agent.setDefaultReasoningEffort(effort);
        }
    }

    private String getCurrentUserId(AgentChatDto dto) {
        // 优先使用DTO中传递的userId（适用于异步线程池场景）
        String userId = dto == null ? null : dto.getUserId();
        return resolveUserId(userId);
    }

    private String resolveUserId(String userId) {
        // 如果DTO中没有，则从CurrentUser获取（适用于同步调用场景）
        if (StringUtils.isBlank(userId)) {
            HashMap<String, String> currentUser = CurrentUser.getUser();
            userId = currentUser == null ? null : currentUser.get("userId");
        }
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, "未授权");
        }
        return userId;
    }

    private AgentDefinition getEnabledAgent(String agentId) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw new ServerException(404, "Agent不存在");
        }
        if (!Integer.valueOf(AGENT_STATUS_ENABLED).equals(agent.getStatus())) {
            throw new ServerException(422, "Agent未启用");
        }
        return agent;
    }

    private ModelProvider getEnabledProvider(String providerId) {
        if (StringUtils.isBlank(providerId)) {
            throw new ServerException(404, "模型供应商不存在");
        }
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())) {
            throw new ServerException(404, "模型供应商不存在");
        }
        if (!Integer.valueOf(PROVIDER_STATUS_ENABLED).equals(provider.getStatus())) {
            throw new ServerException(422, "模型供应商已禁用");
        }
        return provider;
    }

    private AgentConversation getOrCreateConversation(AgentChatDto dto, String userId, AgentDefinition agent) {
        if (StringUtils.isBlank(dto.getConversationId())) {
            AgentConversation conversation = new AgentConversation();
            conversation.setUserId(userId);
            conversation.setAgentDefinitionId(agent.getId());
            conversation.setTitle(buildConversationTitle(dto.getMessage()));
            conversation.setMessageCount(0);
            conversation.setStatus(Boolean.TRUE.equals(dto.getTemporary()) ? 2 : CONVERSATION_STATUS_OPEN);
            agentConversationService.save(conversation);
            return conversation;
        }

        AgentConversation conversation = agentConversationService.getById(dto.getConversationId());
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())) {
            throw new ServerException(404, "会话不存在");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new ServerException(403, "无权访问会话");
        }
        if (!agent.getId().equals(conversation.getAgentDefinitionId())) {
            throw new ServerException(422, "会话与Agent不匹配");
        }
        if (!Integer.valueOf(CONVERSATION_STATUS_OPEN).equals(conversation.getStatus())) {
            throw new ServerException(422, "会话已关闭");
        }
        return conversation;
    }

    private String buildConversationTitle(String message) {
        String title = StringUtils.defaultString(message).trim();
        return title.length() > 50 ? title.substring(0, 50) : title;
    }

    private AgentMessage saveUserMessage(String conversationId, String content) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setMessageType(MESSAGE_TYPE_CHAT);
        message.setContent(content);
        agentMessageService.save(message);
        
        // 更新缓存：添加用户消息
        updateContextCache(conversationId, new ModelChatMessage("user", content));
        
        return message;
    }

    private AgentMessage saveAnswerMessage(String conversationId, String parentMessageId, String content) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setMessageType(MESSAGE_TYPE_ANSWER);
        message.setParentMessageId(parentMessageId);
        message.setContent(content);
        agentMessageService.save(message);
        updateContextCache(conversationId, new ModelChatMessage("user", content));
        return message;
    }

    private void markInteractionStatus(String messageId, String status, Long answeredAt) {
        AgentMessage update = new AgentMessage();
        update.setId(messageId);
        update.setInteractionStatus(status);
        if (answeredAt != null) {
            update.setAnsweredAt(answeredAt);
        }
        agentMessageService.updateById(update);
    }

    private void markInteractionAnswered(AgentMessage question, Map<String, Object> answer, Long answeredAt) {
        AgentMessage update = new AgentMessage();
        update.setId(question.getId());
        update.setInteractionStatus(INTERACTION_STATUS_ANSWERED);
        update.setAnsweredAt(answeredAt);
        update.setQuestionConfig(buildAnsweredQuestionConfig(question, answer, answeredAt));
        agentMessageService.updateById(update);
    }

    private List<ModelChatMessage> buildContext(AgentDefinition agent, String conversationId) {
        // 1. 尝试从缓存读取
        String cacheKey = CONTEXT_CACHE_KEY_PREFIX + conversationId;
        Object cachedContext = redisTemplate.opsForValue().get(cacheKey);
        if (cachedContext != null) {
            try {
                @SuppressWarnings("unchecked")
                List<ModelChatMessage> context = (List<ModelChatMessage>) cachedContext;
                if (context != null && !context.isEmpty()) {
                    return context;
                }
            } catch (Exception e) {
                // 缓存反序列化失败，忽略并重新从数据库构建
            }
        }
        
        // 2. 缓存未命中，从数据库构建
        List<ModelChatMessage> context = buildContextFromDb(agent, conversationId);
        
        // 3. 写入缓存（30分钟过期）
        try {
            redisTemplate.opsForValue().set(cacheKey, context, CONTEXT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
        }
        
        return context;
    }

    /**
     * 从数据库构建对话上下文。
     */
    private List<ModelChatMessage> buildContextFromDb(AgentDefinition agent, String conversationId) {
        List<ModelChatMessage> context = new ArrayList<>();
        if (StringUtils.isNotBlank(agent.getSystemPrompt())) {
            context.add(new ModelChatMessage("system", agent.getSystemPrompt()));
        }

        List<AgentMessage> messages = agentMessageService.list(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant")
                .orderByDesc(AgentMessage::getCreatedAt)
                .last("limit 20"));
        Collections.reverse(messages);
        for (AgentMessage message : messages) {
            context.add(new ModelChatMessage(message.getRole(), message.getContent()));
        }
        return context;
    }

    private AgentMessage saveAssistantMessage(String conversationId, ModelChatResponse modelResponse, long latencyMs) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setMessageType(MESSAGE_TYPE_CHAT);
        message.setContent(modelResponse.getContent());
        message.setReasoningContent(modelResponse.getReasoningContent());
        message.setToolCalls(modelResponse.getToolCalls());
        message.setModel(modelResponse.getModel());
        message.setPromptTokens(modelResponse.getPromptTokens());
        message.setCompletionTokens(modelResponse.getCompletionTokens());
        message.setTotalTokens(modelResponse.getTotalTokens());
        message.setReasoningTokens(modelResponse.getReasoningTokens());
        message.setLatencyMs((int) latencyMs);
        agentMessageService.save(message);
        
        // 更新缓存：添加助手消息
        updateContextCache(conversationId, new ModelChatMessage("assistant", modelResponse.getContent()));
        
        return message;
    }

    private AgentMessage saveAssistantMessage(String conversationId, ModelStreamResponse modelResponse, long latencyMs) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setMessageType(MESSAGE_TYPE_CHAT);
        message.setContent(modelResponse.getContent());
        message.setReasoningContent(modelResponse.getReasoningContent());
        message.setToolCalls(modelResponse.getToolCalls());
        message.setModel(modelResponse.getModel());
        message.setPromptTokens(modelResponse.getPromptTokens());
        message.setCompletionTokens(modelResponse.getCompletionTokens());
        message.setTotalTokens(modelResponse.getTotalTokens());
        message.setReasoningTokens(modelResponse.getReasoningTokens());
        message.setLatencyMs((int) latencyMs);
        agentMessageService.save(message);
        
        // 更新缓存：添加助手消息
        updateContextCache(conversationId, new ModelChatMessage("assistant", modelResponse.getContent()));
        
        return message;
    }

    private AgentMessage saveAssistantPreludeIfPresent(String conversationId, ModelChatResponse response, long latencyMs) {
        if (response == null || StringUtils.isBlank(response.getContent())) {
            return null;
        }
        ModelChatResponse prelude = new ModelChatResponse();
        prelude.setContent(response.getContent());
        prelude.setReasoningContent(response.getReasoningContent());
        prelude.setModel(response.getModel());
        prelude.setPromptTokens(response.getPromptTokens());
        prelude.setCompletionTokens(response.getCompletionTokens());
        prelude.setTotalTokens(response.getTotalTokens());
        prelude.setReasoningTokens(response.getReasoningTokens());
        return saveAssistantMessage(conversationId, prelude, latencyMs);
    }

    private ModelChatResponse toChatResponse(ModelStreamResponse streamResponse) {
        ModelChatResponse response = new ModelChatResponse();
        if (streamResponse != null) {
            response.setContent(streamResponse.getContent());
            response.setReasoningContent(streamResponse.getReasoningContent());
            response.setModel(streamResponse.getModel());
            response.setPromptTokens(streamResponse.getPromptTokens());
            response.setCompletionTokens(streamResponse.getCompletionTokens());
            response.setTotalTokens(streamResponse.getTotalTokens());
            response.setReasoningTokens(streamResponse.getReasoningTokens());
            response.setToolCalls(streamResponse.getToolCalls());
            response.setRawResponse(streamResponse.getRawResponse());
        }
        return response;
    }

    private void validateNonEmptyStreamResponse(ModelStreamResponse response) {
        if (response == null || (StringUtils.isBlank(response.getContent())
                && StringUtils.isBlank(response.getReasoningContent())
                && StringUtils.isBlank(response.getToolCalls()))) {
            throw new ServerException(500, "模型响应内容为空");
        }
    }

    private void updateConversationMessageCount(String conversationId) {
        AgentConversation update = new AgentConversation();
        update.setId(conversationId);
        update.setMessageCount(null); // 避免覆盖
        agentConversationService.update(null, Wrappers.lambdaUpdate(AgentConversation.class)
                .eq(AgentConversation::getId, conversationId)
                .setSql("message_count = message_count + 1"));
    }

    private void saveFailedRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                               String messageId, String input, long latencyMs, RuntimeException e) {
        ModelChatResponse response = new ModelChatResponse();
        response.setModel(agent.getModel());
        saveRun(agent, provider, userId, conversationId, messageId, input, response, latencyMs, RUN_STATUS_FAILED, e.getMessage());
    }

    private String saveRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                         String messageId, String input, ModelChatResponse response, long latencyMs,
                         Integer status, String errorMsg) {
        AgentRun run = new AgentRun();
        run.setAgentDefinitionId(agent.getId());
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setMessageId(messageId);
        run.setInputContent(truncate(input, 1024));
        run.setOutputContent(response == null ? null : truncate(response.getContent(), 1024));
        run.setModel(response == null ? agent.getModel() : response.getModel());
        run.setModelProviderId(provider.getId());
        if (response != null) {
            run.setPromptTokens(response.getPromptTokens());
            run.setCompletionTokens(response.getCompletionTokens());
            run.setTotalTokens(response.getTotalTokens());
        }
        run.setLatencyMs((int) latencyMs);
        run.setStatus(status);
        run.setErrorMsg(truncate(errorMsg, 1024));
        agentRunService.save(run);
        return run.getId();
    }

    private void updateRun(String runId, String messageId, ModelChatResponse response, long latencyMs,
                           Integer status, String errorMsg) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setMessageId(messageId);
        run.setOutputContent(response == null ? null : truncate(response.getContent(), 1024));
        if (response != null) {
            run.setModel(response.getModel());
            run.setPromptTokens(response.getPromptTokens());
            run.setCompletionTokens(response.getCompletionTokens());
            run.setTotalTokens(response.getTotalTokens());
        }
        run.setLatencyMs((int) latencyMs);
        run.setStatus(status);
        run.setErrorMsg(truncate(errorMsg, 1024));
        agentRunService.updateById(run);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private int resolveErrorCode(RuntimeException e) {
        String message = e.getMessage();
        if (StringUtils.isNotBlank(message) && message.indexOf(':') > 0) {
            String code = message.substring(0, message.indexOf(':'));
            if (StringUtils.isNumeric(code)) {
                return Integer.parseInt(code);
            }
        }
        return 500;
    }

    private String resolveErrorMessage(RuntimeException e) {
        String message = e.getMessage();
        if (StringUtils.isNotBlank(message) && message.indexOf(':') > 0) {
            return message.substring(message.indexOf(':') + 1);
        }
        return StringUtils.defaultIfBlank(message, "模型调用失败");
    }

    /**
     * 为ModelStreamResponse补充默认token统计（当模型未提供时）。
     * 例如Google Gemma等模型的流式响应不包含usage字段。
     */
    private void fillDefaultTokens(ModelStreamResponse response, List<ModelChatMessage> context, String inputMessage) {
        if (response == null) {
            return;
        }
        
        // 如果已经有token统计，则不需要处理
        if (response.getPromptTokens() != null || response.getCompletionTokens() != null || response.getTotalTokens() != null) {
            return;
        }
        
        // 估算prompt tokens（基于完整context：system prompt + 历史消息/摘要 + 当前消息）
        int promptTokens = estimateTokensFromContext(context);
        response.setPromptTokens(promptTokens);
        
        // 估算completion tokens（基于输出内容）
        int completionTokens = estimateTokens(response.getContent());
        response.setCompletionTokens(completionTokens);
        
        // 设置total tokens
        response.setTotalTokens(promptTokens + completionTokens);
    }

    /**
     * 基于对话上下文估算prompt token数量。
     * 累加所有消息内容的字符数后估算token数。
     */
    private int estimateTokensFromContext(List<ModelChatMessage> context) {
        if (context == null || context.isEmpty()) {
            return 0;
        }
        int totalLength = 0;
        for (ModelChatMessage message : context) {
            if (StringUtils.isNotBlank(message.getContent())) {
                totalLength += message.getContent().length();
            }
        }
        return (int) Math.ceil(totalLength / 3.0);
    }

    /**
     * 更新会话上下文缓存。
     * 在保存新消息后调用，将新消息追加到缓存中的context。
     */
    private void updateContextCache(String conversationId, ModelChatMessage newMessage) {
        String cacheKey = CONTEXT_CACHE_KEY_PREFIX + conversationId;
        try {
            Object cachedContext = redisTemplate.opsForValue().get(cacheKey);
            if (cachedContext != null) {
                @SuppressWarnings("unchecked")
                List<ModelChatMessage> context = (List<ModelChatMessage>) cachedContext;
                if (context != null) {
                    // 追加新消息
                    context.add(newMessage);
                    
                    // 保持最多21条消息（1 system + 20 messages）
                    if (context.size() > 21) {
                        // 保留 system prompt 和最后 20 条消息
                        if (context.get(0).getRole().equals("system")) {
                            context = new ArrayList<>(context.subList(0, 1)); // system
                            context.addAll(((List<ModelChatMessage>) cachedContext).subList(
                                Math.max(1, ((List<ModelChatMessage>) cachedContext).size() - 20),
                                ((List<ModelChatMessage>) cachedContext).size()
                            ));
                        } else {
                            context = new ArrayList<>(context.subList(context.size() - 21, context.size()));
                        }
                    }
                    
                    // 更新缓存
                    redisTemplate.opsForValue().set(cacheKey, context, CONTEXT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                }
            }
        } catch (Exception e) {
            // 缓存更新失败不影响主流程，下次 buildContext 时会从数据库重建
        }
    }

    /**
     * 估算token数量（当模型未提供准确统计时）。
     * 粗略估算：平均每3个字符约等于1个token。
     */
    private int estimateTokens(String content) {
        if (StringUtils.isBlank(content)) {
            return 0;
        }
        return (int) Math.ceil(content.length() / 3.0);
    }

    /**
     * 生成对话历史摘要。
     * 当对话超过一定轮数时，将早期消息压缩为摘要，减少token消耗。
     */
    private String generateSummary(List<AgentMessage> oldMessages, AgentDefinition agent, ModelProvider provider) {
        if (oldMessages == null || oldMessages.isEmpty()) {
            return "";
        }

        try {
            // 构建摘要请求，限制消息数量避免token过多
            StringBuilder summaryPrompt = new StringBuilder();
            summaryPrompt.append("请将以下对话历史总结为关键要点，保留重要信息、用户意图和上下文，200字以内：\n\n");
            
            int limit = Math.min(oldMessages.size(), SUMMARY_OLD_MESSAGE_LIMIT);
            for (int i = oldMessages.size() - limit; i < oldMessages.size(); i++) {
                AgentMessage msg = oldMessages.get(i);
                String role = msg.getRole().equals("user") ? "用户" : "助手";
                summaryPrompt.append(role).append(": ").append(msg.getContent()).append("\n\n");
            }

            // 调用模型生成摘要
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(Collections.singletonList(new ModelChatMessage("user", summaryPrompt.toString())));

            ModelClient modelClient = modelClientFactory.getClient(provider);
            ModelChatResponse response = modelClient.chat(request);
            
            return response.getContent() != null ? response.getContent() : "";
        } catch (Exception e) {
            // 摘要生成失败，返回空字符串，降级使用完整消息
            return "";
        }
    }

    /**
     * 获取或创建会话摘要。
     * 优先从缓存读取，缓存未命中时调用模型生成。
     */
    private String getOrCreateSummary(String conversationId, List<AgentMessage> oldMessages, 
                                      AgentDefinition agent, ModelProvider provider) {
        // 1. 尝试从缓存读取
        String cacheKey = SUMMARY_CACHE_KEY_PREFIX + conversationId;
        Object cachedSummary = redisTemplate.opsForValue().get(cacheKey);
        if (cachedSummary != null) {
            return cachedSummary.toString();
        }

        // 2. 缓存未命中，异步生成摘要（不阻塞当前请求）
        // 当前请求降级使用最近消息，下次请求即可命中缓存
        List<AgentMessage> snapshot = new ArrayList<>(oldMessages);
        CompletableFuture.runAsync(() -> {
            try {
                String summary = generateSummary(snapshot, agent, provider);
                if (StringUtils.isNotBlank(summary)) {
                    redisTemplate.opsForValue().set(cacheKey, summary, SUMMARY_CACHE_TTL_HOURS, TimeUnit.HOURS);
                }
            } catch (Exception e) {
                log.warn("异步生成摘要失败, conversationId={}", conversationId, e);
            }
        }, summaryExecutor);

        return "";
    }

    /**
     * 构建带摘要的对话上下文。
     * 当对话超过阈值时，使用摘要+最近消息的模式。
     */
    private List<ModelChatMessage> buildContextWithSummary(AgentDefinition agent, ModelProvider provider, 
                                                           String conversationId) {
        List<ModelChatMessage> context = new ArrayList<>();
        
        // 1. 添加 system prompt
        if (StringUtils.isNotBlank(agent.getSystemPrompt())) {
            context.add(new ModelChatMessage("system", agent.getSystemPrompt()));
        }

        // 2. 查询最近的消息（限制数量，避免全表扫描）
        int fetchLimit = SUMMARY_TRIGGER_THRESHOLD + KEEP_RECENT_MESSAGES + 1;
        List<AgentMessage> allMessages = agentMessageService.list(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant")
                .orderByAsc(AgentMessage::getCreatedAt)
                .last("limit " + fetchLimit));

        if (allMessages.size() <= SUMMARY_TRIGGER_THRESHOLD) {
            // 消息较少，直接返回完整消息
            for (AgentMessage message : allMessages) {
                context.add(new ModelChatMessage(message.getRole(), message.getContent()));
            }
        } else {
            // 3. 消息较多，使用摘要模式
            int oldMessageCount = allMessages.size() - KEEP_RECENT_MESSAGES;
            List<AgentMessage> oldMessages = allMessages.subList(0, oldMessageCount);
            List<AgentMessage> recentMessages = allMessages.subList(oldMessageCount, allMessages.size());

            // 4. 获取或生成摘要
            String summary = getOrCreateSummary(conversationId, oldMessages, agent, provider);
            if (StringUtils.isNotBlank(summary)) {
                context.add(new ModelChatMessage("system", "【对话历史摘要】" + summary));
            }

            // 5. 添加最近的完整消息
            for (AgentMessage message : recentMessages) {
                context.add(new ModelChatMessage(message.getRole(), message.getContent()));
            }
        }

        return context;
    }

    /**
     * 检测模型响应是否包含工具调用
     */
    private boolean hasToolCalls(ModelChatResponse response) {
        if (response == null || StringUtils.isBlank(response.getToolCalls())) {
            return false;
        }

        try {
            JSONArray toolCalls = JSONArray.parseArray(response.getToolCalls());
            return toolCalls != null && !toolCalls.isEmpty();
        } catch (Exception e) {
            log.warn("解析工具调用失败", e);
            return false;
        }
    }

    private boolean hasSuccessfulToolResult(List<ToolExecutionResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return false;
        }
        for (ToolExecutionResult result : toolResults) {
            if (result != null && result.isSuccess() && Integer.valueOf(TOOL_CALL_STATUS_SUCCESS).equals(result.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private void addToolResultsToContext(List<ModelChatMessage> context,
                                         ModelChatResponse response,
                                         List<ToolExecutionResult> toolResults) {
        if (context == null || response == null || toolResults == null || toolResults.isEmpty()) {
            return;
        }
        context.add(new ModelChatMessage("assistant", response.getContent(), response.getToolCalls(), null));
        Map<String, String> toolNameByCallId = parseToolNameByCallId(response.getToolCalls());
        for (ToolExecutionResult result : toolResults) {
            if (result == null) {
                continue;
            }
            String toolContent = result.isSuccess()
                    ? result.getContent()
                    : buildToolRetryInstruction(toolNameByCallId.get(result.getToolCallId()), result);
            if (toolContent == null) {
                toolContent = result.isSuccess() ? "" : "工具执行失败";
            }
            log.info("工具结果添加到上下文: toolCallId={}, success={}, content={}",
                    result.getToolCallId(), result.isSuccess(), toolContent);
            context.add(new ModelChatMessage("tool", toolContent, null, result.getToolCallId()));
        }
    }

    private Map<String, String> parseToolNameByCallId(String toolCallsJson) {
        Map<String, String> result = new HashMap<>();
        if (StringUtils.isBlank(toolCallsJson)) {
            return result;
        }
        try {
            JSONArray toolCalls = JSONArray.parseArray(toolCallsJson);
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                if (toolCall == null) {
                    continue;
                }
                String id = toolCall.getString("id");
                JSONObject function = toolCall.getJSONObject("function");
                if (StringUtils.isNotBlank(id) && function != null) {
                    result.put(id, function.getString("name"));
                }
            }
        } catch (Exception e) {
            log.warn("解析工具调用名称失败: {}", e.getMessage());
        }
        return result;
    }

    private String buildToolRetryInstruction(String toolName, ToolExecutionResult result) {
        String error = StringUtils.defaultIfBlank(result.getErrorMsg(), result.getContent());
        if (StringUtils.isBlank(error)) {
            error = "工具执行失败";
        }
        error = truncate(error, 2048);
        String name = StringUtils.defaultIfBlank(toolName, "当前工具");
        return "工具 " + name + " 执行失败。\n"
                + "失败原因：" + error + "\n"
                + "请根据该工具的参数 schema 和用户原始请求修正 arguments，并重新调用该工具。"
                + "不要直接编造工具结果；如果无法修复参数，请向用户说明需要补充哪些信息。";
    }

    private void applyInteractiveQuestionPolicy(List<ModelChatMessage> context) {
        if (context == null) {
            return;
        }
        context.add(new ModelChatMessage("system", INTERACTIVE_QUESTION_POLICY));
    }

    private ModelChatResponse retryAskUserWhenPlainQuestion(ModelChatResponse response,
                                                             ModelClient modelClient,
                                                             ModelChatRequest request) {
        if (response == null || hasToolCalls(response) || !looksLikeUserQuestion(response.getContent())) {
            return response;
        }
        log.warn("交互式模式下模型返回了普通问句，强制重试 ask_user: content={}", truncate(response.getContent(), 200));
        try {
            request.setToolChoiceName(ASK_USER_TOOL_NAME);
            return modelClient.chat(request);
        } finally {
            request.setToolChoiceName(null);
        }
    }

    private boolean looksLikeUserQuestion(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String text = content.trim();
        if (text.length() > 500) {
            return false;
        }
        if (text.contains("?") || text.contains("？")) {
            return true;
        }
        String lower = text.toLowerCase();
        return lower.contains("please choose")
                || lower.contains("please confirm")
                || lower.contains("do you want")
                || text.contains("请选择")
                || text.contains("请确认")
                || text.contains("是否")
                || text.contains("要不要")
                || text.contains("需要你")
                || text.contains("请提供")
                || text.contains("请填写");
    }

    private ToolResult handleInternalToolCall(String conversationId, ModelChatResponse response, long latencyMs) {
        for (ToolCallInfo toolCall : parseToolCalls(response)) {
            Tool handler = toolRegistry.getHandler(toolCall.getName());
            if (handler == null) {
                continue;
            }
            try {
                ToolResult result = handler.handle(conversationId, toolCall.getArguments());
                if (StringUtils.isNotBlank(result.getContextContent())) {
                    updateContextCache(conversationId, new ModelChatMessage("assistant", result.getContextContent()));
                }
                return result;
            } catch (ServerException e) {
                log.warn("内建工具参数不合法，降级为普通助手消息: conversationId={}, tool={}, reason={}",
                        conversationId, toolCall.getName(), e.getMessage());
                ModelChatResponse fallback = new ModelChatResponse();
                fallback.setContent(extractFirstQuestionText(toolCall));
                AgentMessage message = saveAssistantMessage(conversationId, fallback, latencyMs);
                return ToolResult.waitingUser(message, null);
            }
        }
        return null;
    }

    private boolean hasInternalToolCall(ModelChatResponse response) {
        for (ToolCallInfo toolCall : parseToolCalls(response)) {
            if (toolRegistry.getHandler(toolCall.getName()) != null) {
                return true;
            }
        }
        return false;
    }

    private String extractFirstQuestionText(ToolCallInfo toolCall) {
        if (toolCall != null && toolCall.getArguments() != null) {
            Object questionsObj = toolCall.getArguments().get("questions");
            if (questionsObj instanceof List) {
                List<?> questions = (List<?>) questionsObj;
                if (!questions.isEmpty() && questions.get(0) instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> first = (Map<String, Object>) questions.get(0);
                    Object question = first.get("question");
                    if (question != null && StringUtils.isNotBlank(question.toString())) {
                        return truncate(question.toString(), 1000);
                    }
                }
            }
        }
        return "请补充必要信息后继续。";
    }

    private String renderAnswerContent(AgentMessage question, Map<String, Object> answer) {
        if (answer == null) {
            throw new ServerException(400, "回复内容不能为空");
        }
        JSONObject config = JSONObject.parseObject(question.getQuestionConfig());
        String type = config.getString("type");
        if ("group".equals(type)) {
            return renderGroupAnswer(config, answer);
        }
        if ("choice".equals(type)) {
            return renderChoiceAnswer(config, answer);
        }
        if ("confirm".equals(type)) {
            Object confirmed = answer.get("confirmed");
            if (!(confirmed instanceof Boolean)) {
                throw new ServerException(400, "confirm回复必须包含confirmed布尔值");
            }
            return "用户选择：" + (Boolean.TRUE.equals(confirmed) ? "确认" : "取消");
        }
        throw new ServerException(400, "未知提问类型");
    }

    private String buildAnsweredQuestionConfig(AgentMessage question, Map<String, Object> answer, Long answeredAt) {
        JSONObject config = JSONObject.parseObject(question.getQuestionConfig());
        String type = config.getString("type");
        JSONObject answerConfig = new JSONObject();
        answerConfig.put("answeredAt", answeredAt);

        if ("group".equals(type)) {
            Object answersObj = answer.get("answers");
            @SuppressWarnings("unchecked")
            Map<String, Object> answers = (Map<String, Object>) answersObj;
            JSONArray questions = config.getJSONArray("questions");
            JSONObject normalizedAnswers = new JSONObject();
            for (int i = 0; i < questions.size(); i++) {
                JSONObject item = questions.getJSONObject(i);
                String id = item.getString("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> itemAnswer = (Map<String, Object>) answers.get(id);
                JSONObject displayAnswer = buildDisplayAnswer(item, itemAnswer);
                item.put("answer", displayAnswer);
                normalizedAnswers.put(id, displayAnswer);
            }
            answerConfig.put("answers", normalizedAnswers);
            config.put("answer", answerConfig);
            return config.toJSONString();
        }

        JSONObject displayAnswer = buildDisplayAnswer(config, answer);
        config.put("answer", displayAnswer);
        answerConfig.put("value", displayAnswer);
        config.put("answered", answerConfig);
        return config.toJSONString();
    }

    private JSONObject buildDisplayAnswer(JSONObject config, Map<String, Object> answer) {
        String type = config.getString("type");
        if ("choice".equals(type)) {
            return buildChoiceDisplayAnswer(config, answer);
        }
        if ("confirm".equals(type)) {
            Object confirmed = answer.get("confirmed");
            JSONObject result = new JSONObject();
            result.put("confirmed", confirmed);
            result.put("label", Boolean.TRUE.equals(confirmed)
                    ? StringUtils.defaultIfBlank(config.getString("confirmText"), "确认")
                    : StringUtils.defaultIfBlank(config.getString("cancelText"), "取消"));
            return result;
        }
        throw new ServerException(400, "未知提问类型");
    }

    private JSONObject buildChoiceDisplayAnswer(JSONObject config, Map<String, Object> answer) {
        Object selectedObj = answer.get("selected");
        List<String> selected = normalizeSelectedValues(selectedObj);
        JSONArray selectedOptions = new JSONArray();
        JSONArray options = config.getJSONArray("options");
        for (String value : selected) {
            JSONObject matched = null;
            for (int i = 0; i < options.size(); i++) {
                JSONObject option = options.getJSONObject(i);
                if (value.equals(option.getString("id")) || value.equals(option.getString("value"))) {
                    matched = option;
                    break;
                }
            }
            if (matched != null) {
                JSONObject selectedOption = new JSONObject();
                selectedOption.put("id", matched.getString("id"));
                selectedOption.put("label", matched.getString("label"));
                selectedOption.put("value", matched.getString("value"));
                selectedOptions.add(selectedOption);
            }
        }

        JSONObject result = new JSONObject();
        if (Boolean.TRUE.equals(config.getBoolean("multiple"))) {
            result.put("selected", selected);
        } else {
            result.put("selected", selected.isEmpty() ? null : selected.get(0));
        }
        result.put("selectedOptions", selectedOptions);
        return result;
    }

    private String renderGroupAnswer(JSONObject groupConfig, Map<String, Object> answer) {
        Object answersObj = answer.get("answers");
        if (!(answersObj instanceof Map)) {
            throw new ServerException(400, "group回复必须包含answers对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) answersObj;
        JSONArray questions = groupConfig.getJSONArray("questions");
        if (questions == null || questions.isEmpty()) {
            throw new ServerException(400, "提问配置不合法");
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            JSONObject item = questions.getJSONObject(i);
            String id = item.getString("id");
            Object itemAnswerObj = answers.get(id);
            if (!(itemAnswerObj instanceof Map)) {
                throw new ServerException(400, "缺少问题回复: " + id);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> itemAnswer = (Map<String, Object>) itemAnswerObj;
            lines.add(item.getString("question") + "：" + renderSingleGroupAnswer(item, itemAnswer));
        }
        return "用户回复：" + StringUtils.join(lines, "；");
    }

    private String renderSingleGroupAnswer(JSONObject config, Map<String, Object> answer) {
        String type = config.getString("type");
        if ("choice".equals(type)) {
            return renderChoiceAnswer(config, answer).replaceFirst("^用户选择：", "");
        }
        if ("confirm".equals(type)) {
            Object confirmed = answer.get("confirmed");
            if (!(confirmed instanceof Boolean)) {
                throw new ServerException(400, "confirm回复必须包含confirmed布尔值");
            }
            return Boolean.TRUE.equals(confirmed) ? "确认" : "取消";
        }
        throw new ServerException(400, "未知提问类型");
    }

    private String renderChoiceAnswer(JSONObject config, Map<String, Object> answer) {
        Object selectedObj = answer.get("selected");
        List<String> selected = normalizeSelectedValues(selectedObj);
        if (selected.isEmpty()) {
            throw new ServerException(400, "choice回复必须包含selected");
        }
        boolean multiple = Boolean.TRUE.equals(config.getBoolean("multiple"));
        if (!multiple && selected.size() > 1) {
            throw new ServerException(400, "该提问只能选择一个选项");
        }

        JSONArray options = config.getJSONArray("options");
        List<String> labels = new ArrayList<>();
        for (String value : selected) {
            JSONObject matched = null;
            for (int i = 0; i < options.size(); i++) {
                JSONObject option = options.getJSONObject(i);
                if (value.equals(option.getString("id")) || value.equals(option.getString("value"))) {
                    matched = option;
                    break;
                }
            }
            if (matched == null) {
                throw new ServerException(400, "回复选项不在允许范围内");
            }
            labels.add(matched.getString("label") + "(" + matched.getString("value") + ")");
        }
        return "用户选择：" + StringUtils.join(labels, ", ");
    }

    private List<String> normalizeSelectedValues(Object selectedObj) {
        List<String> selected = new ArrayList<>();
        if (selectedObj instanceof List) {
            for (Object item : (List<?>) selectedObj) {
                if (item != null) {
                    selected.add(item.toString());
                }
            }
        } else if (selectedObj != null) {
            selected.add(selectedObj.toString());
        }
        return selected;
    }

    private ToolAuthenticityCheck checkToolAuthenticity(String content, boolean toolCallAttempted, boolean toolCallSucceeded) {
        if (toolCallAttempted && !toolCallSucceeded) {
            return ToolAuthenticityCheck.invalid("工具调用已触发但没有成功执行记录");
        }
        if (!toolCallSucceeded && claimsToolBackedResult(content)) {
            return ToolAuthenticityCheck.invalid("模型声称使用了工具或接口，但本轮没有成功工具执行记录");
        }
        return ToolAuthenticityCheck.valid();
    }

    private boolean claimsToolBackedResult(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String normalized = content.toLowerCase();
        String[] explicitClaims = new String[] {
                "已调用工具",
                "调用了工具",
                "工具返回",
                "工具结果",
                "根据工具",
                "已调用接口",
                "调用了接口",
                "接口返回",
                "接口结果",
                "根据接口",
                "已调用api",
                "调用了api",
                "api返回",
                "api 返回",
                "根据api",
                "tool returned",
                "tool result",
                "called the tool",
                "called an api",
                "api returned",
                "according to the api"
        };
        for (String claim : explicitClaims) {
            if (normalized.contains(claim)) {
                return true;
            }
        }
        return false;
    }

    private String buildToolAuthenticityFallback(ToolAuthenticityCheck check) {
        return "工具调用未获得可信结果，已阻止生成可能不准确的工具结果。"
                + "请稍后重试，或检查工具配置。原因：" + check.getReason();
    }

    /**
     * 执行工具调用
     */
    private List<ToolExecutionResult> executeToolCalls(ModelChatResponse modelResponse, AgentDefinition agent,
                                                       String userId, String runId) {
        List<ToolExecutionResult> results = new ArrayList<>();

        try {
            // 解析工具调用
            List<ToolCallInfo> toolCalls = parseToolCalls(modelResponse);
            if (toolCalls.isEmpty()) {
                return results;
            }

            // 获取Agent绑定的工具
            List<AgentTool> boundTools = getBoundTools(agent.getId());
            Map<String, AgentTool> toolMap = new HashMap<>();
            for (AgentTool tool : boundTools) {
                toolMap.put(tool.getName(), tool);
            }

            // 执行每个工具调用
            for (ToolCallInfo toolCall : toolCalls) {
                AgentTool tool = toolMap.get(toolCall.getName());
                if (tool == null) {
                    log.warn("工具未找到: {}", toolCall.getName());
                    ToolExecutionResult failure = ToolExecutionResult.failure("工具未找到: " + toolCall.getName(), 1);
                    failure.setToolCallId(toolCall.getId());
                    failure.setRequestMethod("MCP tools/call");
                    results.add(failure);
                    saveToolCallLog(runId, toolCall.getId(), toolCall.getName(),
                            JSON.toJSONString(toolCall.getArguments()),
                            null, agent.getId(), null, null, null,
                            null, null, null, null, 1, "工具未找到: " + toolCall.getName());
                    continue;
                }

                log.info("执行工具调用: name={}, toolId={}, arguments={}", toolCall.getName(), tool.getId(), toolCall.getArguments());
                
                ToolExecutionContext context = new ToolExecutionContext();
                context.setTool(tool);
                context.setArguments(toolCall.getArguments());
                context.setRunId(runId);
                context.setUserId(userId);

                try {
                    ToolExecutor executor = toolExecutorFactory.getExecutor("mcp");
                    ToolExecutionResult result = executor.execute(context);
                    result.setToolCallId(toolCall.getId());
                    results.add(result);

                    log.info("工具执行完成: name={}, status={}, content={}", toolCall.getName(), result.getStatus(), result.getContent());
                    
                    // 保存工具调用日志
                    saveToolCallLog(
                            runId,
                            toolCall.getId(),
                            toolCall.getName(),
                            JSON.toJSONString(toolCall.getArguments()),
                            tool.getId(),
                            agent.getId(),
                            result.getRequestUrl(),
                            result.getRequestMethod(),
                            result.getRequestHeaders(),
                            result.getRequestBody(),
                            result.getHttpStatus(),
                            result.getRawResponse(),
                            result.getLatencyMs(),
                            result.getStatus(),
                            result.getErrorMsg()
                    );
                } catch (Exception e) {
                    log.error("工具执行异常: {}", tool.getCode(), e);
                    saveToolCallLog(runId, toolCall.getId(), toolCall.getName(),
                            JSON.toJSONString(toolCall.getArguments()),
                            tool.getId(), agent.getId(), null,
                            "MCP tools/call", null, null, null, null, null,
                            1, e.getMessage());
                    ToolExecutionResult failure = ToolExecutionResult.failure(e.getMessage(), 1);
                    failure.setToolCallId(toolCall.getId());
                    failure.setRequestUrl(null);
                    failure.setRequestMethod("MCP tools/call");
                    results.add(failure);
                }
            }
        } catch (Exception e) {
            log.error("工具调用处理失败", e);
        }

        return results;
    }

    /**
     * 解析模型返回的工具调用
     */
    private List<ToolCallInfo> parseToolCalls(ModelChatResponse response) {
        List<ToolCallInfo> toolCalls = new ArrayList<>();

        try {
            if (StringUtils.isBlank(response.getToolCalls())) {
                return toolCalls;
            }
            JSONArray toolCallsArray = JSONArray.parseArray(response.getToolCalls());
            if (toolCallsArray == null || toolCallsArray.isEmpty()) {
                return toolCalls;
            }

            for (int i = 0; i < toolCallsArray.size(); i++) {
                JSONObject toolCall = toolCallsArray.getJSONObject(i);
                String id = toolCall.getString("id");
                String name = toolCall.getJSONObject("function").getString("name");
                String argumentsStr = toolCall.getJSONObject("function").getString("arguments");

                Map<String, Object> arguments = new HashMap<>();
                if (StringUtils.isNotBlank(argumentsStr)) {
                    arguments = JSON.parseObject(argumentsStr, Map.class);
                }
                log.info("解析工具调用: id={}, name={}, argumentsRaw={}, arguments={}", id, name, argumentsStr, arguments);

                toolCalls.add(new ToolCallInfo(id, name, arguments));
            }
        } catch (Exception e) {
            log.error("解析工具调用失败", e);
        }

        return toolCalls;
    }

    /**
     * 获取Agent绑定的工具列表
     */
    private List<AgentTool> getRequestTools(String agentId) {
        List<AgentTool> tools = new ArrayList<>(getBoundTools(agentId));
        tools.addAll(toolRegistry.getTools());
        return tools;
    }

    private List<AgentTool> getBoundTools(String agentId) {
        String cacheKey = TOOLS_CACHE_KEY_PREFIX + agentId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                List<AgentTool> tools = (List<AgentTool>) cached;
                return tools;
            }
        } catch (Exception e) {
            // 缓存读取失败，降级查库
        }

        List<AgentToolBinding> bindings = agentToolBindingService.list(
                Wrappers.lambdaQuery(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, agentId)
                        .eq(AgentToolBinding::getStatus, 1)
                        .eq(AgentToolBinding::getDeleted, false)
                        .orderByAsc(AgentToolBinding::getPriority)
        );

        List<AgentTool> tools = new ArrayList<>();
        for (AgentToolBinding binding : bindings) {
            AgentTool tool = agentToolService.getById(binding.getToolId());
            if (tool != null && !Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus())) {
                tools.add(tool);
            }
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, tools, TOOLS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
        }

        return tools;
    }

    /**
     * 清除指定工具相关的所有Agent缓存
     */
    public void evictToolCacheByToolId(String toolId) {
        try {
            // 查找所有绑定该工具的Agent
            List<AgentToolBinding> bindings = agentToolBindingService.list(
                    Wrappers.lambdaQuery(AgentToolBinding.class)
                            .eq(AgentToolBinding::getToolId, toolId)
                            .eq(AgentToolBinding::getDeleted, false)
            );
            for (AgentToolBinding binding : bindings) {
                evictToolCache(binding.getAgentDefinitionId());
            }
        } catch (Exception e) {
            // 清除缓存失败不影响主流程
        }
    }

    /**
     * 清除指定Agent的工具缓存
     */
    public void evictToolCache(String agentId) {
        try {
            String cacheKey = TOOLS_CACHE_KEY_PREFIX + agentId;
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // 清除缓存失败不影响主流程
        }
    }

    /**
     * 保存工具调用日志
     */
    private void saveToolCallLog(String runId, String toolCallId, String toolName, String argumentsJson,
                                 String toolId, String agentDefinitionId,
                                 String requestUrl, String requestMethod, String requestHeaders,
                                 String requestBody, Integer responseStatus, String responseBody,
                                 Integer latencyMs, Integer status, String errorMsg) {
        AgentToolCallLog log = new AgentToolCallLog();
        log.setRunId(runId);
        log.setToolCallId(toolCallId);
        log.setToolName(toolName);
        log.setArguments(truncate(argumentsJson, 65536));
        log.setToolId(toolId);
        log.setAgentDefinitionId(agentDefinitionId);
        log.setRequestUrl(truncate(requestUrl, 2048));
        log.setRequestMethod(requestMethod);
        log.setRequestHeaders(requestHeaders);
        log.setRequestBody(truncate(requestBody, 65536));
        log.setResponseStatus(responseStatus);
        log.setResponseBody(truncate(responseBody, 65536));
        log.setLatencyMs(latencyMs);
        log.setStatus(status);
        log.setErrorMsg(truncate(errorMsg, 1024));
        agentToolCallLogService.save(log);
    }

    /**
     * 工具调用信息
     */
    private static class ToolCallInfo {
        private final String id;
        private final String name;
        private final Map<String, Object> arguments;

        public ToolCallInfo(String id, String name, Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }
    }

    private static class ToolAuthenticityCheck {
        private final boolean valid;
        private final String reason;

        private ToolAuthenticityCheck(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        static ToolAuthenticityCheck valid() {
            return new ToolAuthenticityCheck(true, null);
        }

        static ToolAuthenticityCheck invalid(String reason) {
            return new ToolAuthenticityCheck(false, reason);
        }

        boolean isValid() {
            return valid;
        }

        String getReason() {
            return reason;
        }
    }
}
