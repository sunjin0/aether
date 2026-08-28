package com.aether.agent.service.impl;

import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.observability.ChatLatencyMetrics;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.tools.entity.ToolResult;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.agent.tools.AgentToolWorkflow.ApprovalExecution;
import com.aether.agent.tools.core.Tool;
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
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.service.InteractionReplyService;
import com.aether.agent.service.ConversationContextService;
import com.aether.agent.service.ContextMetricService;
import com.aether.agent.service.CapabilityIndexService;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.service.QueryRewriteService;
import com.aether.agent.service.ChatRunService;
import com.aether.agent.service.RuntimeEmailCredentialStore;
import com.aether.agent.service.AdminPreferenceExtractionService;
import com.aether.agent.service.AgentSessionMemoryExtractionService;
import com.aether.agent.skill.service.SkillContextService;
import com.aether.agent.skill.service.SkillRuntimeContext;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final String MESSAGE_TYPE_TOOL_CALL = "tool_call";
    private static final String MESSAGE_TYPE_TOOL_RESULT = "tool_result";
    private static final String INTERACTION_STATUS_PENDING = "pending";
    private static final String INTERACTION_STATUS_ANSWERED = "answered";
    private static final int TOOL_CALL_STATUS_SUCCESS = 0;
    private static final int MAX_TOOL_CALL_ITERATIONS = 5; // 最大工具调用迭代次数
    /**
     * Keep one verbose MCP response from consuming the following model turn's context window.
     */
    private static final int MAX_TOOL_CONTEXT_CHARS = 6000;

    private final AgentDefinitionService agentDefinitionService;
    private final ModelProviderService modelProviderService;
    private final ModelCatalogService modelCatalogService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final ChatRunService chatRunService;
    private final ModelClientFactory modelClientFactory;
    private final AgentToolWorkflow agentToolWorkflow;
    private final KnowledgeContextService knowledgeContextService;
    private final InteractionReplyService interactionReplyService;
    private final ConversationContextService conversationContextService;
    private final AdminPreferenceExtractionService adminPreferenceExtractionService;
    private final AgentSessionMemoryExtractionService sessionMemoryExtractionService;
    private final QueryRewriteService queryRewriteService;
    private final SkillContextService skillContextService;

    @Autowired(required = false)
    private SkillArtifactExecutionService artifactExecutionService;

    /** Optional to preserve direct construction used by legacy unit tests. */
    @Autowired(required = false)
    private ContextMetricService contextMetricService;

    /** Optional to preserve direct construction used by legacy unit tests. */
    @Autowired(required = false)
    private CapabilityIndexService capabilityIndexService;

    /** 运行时邮件凭据只绑定到本次 run，绝不进入模型上下文或数据库。 */
    @Autowired(required = false)
    private RuntimeEmailCredentialStore runtimeEmailCredentialStore;

    /**
     * 默认关闭，避免每轮聊天在主模型调用前额外等待一次同步模型重写。
     */
    @Value("${agent.chat.query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    /**
     * 创建 {@code AgentChatServiceImpl} 实例。
     */
    @Autowired
    public AgentChatServiceImpl(AgentDefinitionService agentDefinitionService,
                                ModelProviderService modelProviderService,
                                AgentConversationService agentConversationService,
                                AgentMessageService agentMessageService,
                                ChatRunService chatRunService,
                                ModelClientFactory modelClientFactory,
                                AgentToolWorkflow agentToolWorkflow,
                                KnowledgeContextService knowledgeContextService,
                                InteractionReplyService interactionReplyService,
                                ConversationContextService conversationContextService,
                                AdminPreferenceExtractionService adminPreferenceExtractionService,
                                AgentSessionMemoryExtractionService sessionMemoryExtractionService,
                                QueryRewriteService queryRewriteService, SkillContextService skillContextService,
                                ModelCatalogService modelCatalogService) {
        this.agentDefinitionService = agentDefinitionService;
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.chatRunService = chatRunService;
        this.modelClientFactory = modelClientFactory;
        this.agentToolWorkflow = agentToolWorkflow;
        this.knowledgeContextService = knowledgeContextService;
        this.interactionReplyService = interactionReplyService;
        this.conversationContextService = conversationContextService;
        this.adminPreferenceExtractionService = adminPreferenceExtractionService;
        this.sessionMemoryExtractionService = sessionMemoryExtractionService;
        this.queryRewriteService = queryRewriteService;
        this.skillContextService = skillContextService;
    }

    /**
     * 保持既有单元测试和非 Spring 手工构造调用兼容，运行时由完整构造器注入 Skill 服务。
     */
    public AgentChatServiceImpl(AgentDefinitionService agentDefinitionService,
                                ModelProviderService modelProviderService,
                                AgentConversationService agentConversationService,
                                AgentMessageService agentMessageService,
                                ChatRunService chatRunService,
                                ModelClientFactory modelClientFactory,
                                AgentToolWorkflow agentToolWorkflow,
                                KnowledgeContextService knowledgeContextService,
                                InteractionReplyService interactionReplyService,
                                ConversationContextService conversationContextService,
                                AdminPreferenceExtractionService adminPreferenceExtractionService,
                                QueryRewriteService queryRewriteService) {
        this(agentDefinitionService, modelProviderService, agentConversationService, agentMessageService, chatRunService,
                modelClientFactory, agentToolWorkflow, knowledgeContextService, interactionReplyService,
                conversationContextService, adminPreferenceExtractionService, null, queryRewriteService, null, null);
    }

    /**
     * 对话当前请求。
     */
    @Override
    public AgentMessageVo chat(AgentChatDto dto) {
        validateRequest(dto);
        String userId = getCurrentUserId(dto);
        long startTime = System.currentTimeMillis();

        AgentDefinition enabledAgent = getEnabledAgent(dto.getAgentId());
        AgentDefinition agent = resolveRuntimeAgent(dto, enabledAgent);
        ModelProvider provider = getEnabledProvider(agent);
        applyThinkingConfig(dto, agent);
        AgentConversation conversation = getOrCreateConversation(dto, userId, agent);
        stageRuntimeEmailSecrets(conversation.getId(), userId, dto);
        String rewrittenContent = rewriteUserMessage(conversation.getId(), dto.getMessage(), agent, provider);
        AgentMessage userMessage = saveUserMessage(conversation.getId(), dto.getMessage(), rewrittenContent,
                dto.getAttachmentContent(), dto.getAttachments(), agent, provider);
        String runId = null;

        try {
SkillRuntimeContext skillContext = resolveSkillContext(agent, dto, effectiveContent(rewrittenContent, dto.getMessage()), provider);
            List<ModelChatMessage> context = buildContextWithSummary(agent, provider, conversation.getId(), userId);
            applySkillPrompt(context, skillContext);
            List<Map<String, Object>> sources = knowledgeContextService.enhance(
                    context, userId, conversation.getId(), agent.getId(), effectiveContent(rewrittenContent, dto.getMessage()), skillContext.getKnowledgeBaseIds(), dto.getRetrievalMode());
            enforceSkillBudget(context, agent, provider, skillContext);
            conversationContextService.enforceBudget(context, agent, provider);
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);
            request.setTools(agentToolWorkflow.getRequestTools(skillContext.getTools(),
                    effectiveContent(rewrittenContent, dto.getMessage()), skillContext.getRequiredToolIds()));

            // 在任何模型调用前冻结本次 Skill 装配结果，失败运行同样可追溯。
            runId = saveRun(agent, provider, userId, conversation.getId(), userMessage.getId(),
                    modelInputSnapshot(request), null, 0, RUN_STATUS_SUCCESS, null, skillContext.getSnapshot());

            ModelClient modelClient = modelClientFactory.getClient(provider);
            ModelChatResponse modelResponse = dispatchChat(modelClient, request, runId, 1);
            // ask_user 的强制重试仅服务于聊天交互模式；工作流已由人工节点统一处理，
            // 不能让普通 Agent 节点因模型回答中包含问句而额外发起一次模型调用。
            if (Boolean.TRUE.equals(dto.getInteractive())) {
                modelResponse = retryAskUserWhenPlainQuestion(modelResponse, modelClient, request, runId, 2);
            }

            // 推理未开启时，过滤掉模型可能返回的reasoning_content和reasoning_tokens
            if (!Boolean.TRUE.equals(agent.getDefaultThinking()) && StringUtils.isBlank(modelResponse.getToolCalls())) {
                modelResponse.setReasoningContent(null);
                modelResponse.setReasoningTokens(null);
            }

            // 处理工具调用
            int iteration = 0;
            boolean toolCallAttempted = false;
            boolean toolCallSucceeded = false;
            while (hasToolCalls(modelResponse) && iteration < MAX_TOOL_CALL_ITERATIONS) {
                if (runId == null) {
                    runId = saveRun(agent, provider, userId, conversation.getId(), userMessage.getId(), effectiveContent(rewrittenContent, dto.getMessage()), modelResponse, 0, RUN_STATUS_SUCCESS, null, skillContext.getSnapshot());
                }
                iteration++;
                toolCallAttempted = true;
                attachCitedSources(modelResponse, sources);
                AgentMessage assistantPrelude = saveAssistantPreludeIfPresent(
                        conversation.getId(), modelResponse, System.currentTimeMillis() - startTime, agent, provider);
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
                // Persist any model text produced before it requested the tool.
                // The non-streaming response can only return one message, so the
                // prelude is recovered through normal conversation history.
                AgentMessage approval = agentToolWorkflow.createMcpApproval(conversation.getId(), modelResponse, agent, userId, runId, skillContext.getTools());
                if (approval != null) {
                    updateConversationMessageCount(conversation.getId());
                    updateRun(runId, approval.getId(), modelResponse, System.currentTimeMillis() - startTime,
                            RUN_STATUS_WAITING_USER, null);
                    AgentMessageVo vo = new AgentMessageVo();
                    BeanUtils.copyProperties(approval, vo);
                    return vo;
                }
                List<ToolExecutionResult> toolResults = agentToolWorkflow.executeMcpCalls(modelResponse, agent, userId, runId, skillContext.getTools());
                saveToolResultMessages(conversation.getId(), toolResults, agent, provider);
                toolCallSucceeded = toolCallSucceeded || hasSuccessfulToolResult(toolResults);

                addToolResultsToContext(context, modelResponse, toolResults);
                enforceSkillBudget(context, agent, provider, skillContext);
                chatRunService.updateSkillSnapshot(runId, skillContext.getSnapshot());
                conversationContextService.enforceBudget(context, agent, provider);

                // 继续调用模型
                request.setMessages(context);
                modelResponse = dispatchChat(modelClient, request, runId, iteration + 1);
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            ToolAuthenticityCheck authenticityCheck = checkToolAuthenticity(modelResponse.getContent(), toolCallAttempted, toolCallSucceeded);
            if (!authenticityCheck.isValid()) {
                log.warn("拦截疑似工具结果幻觉回答: conversationId={}, attempted={}, succeeded={}, reason={}",
                        conversation.getId(), toolCallAttempted, toolCallSucceeded, authenticityCheck.getReason());
                ModelChatResponse retryResponse = retryToolAuthenticity(modelClient, request, modelResponse, authenticityCheck, runId);
                ToolAuthenticityCheck retryCheck = checkToolAuthenticity(retryResponse.getContent(), false, false);
                if (retryCheck.isValid()) {
                    modelResponse = retryResponse;
                    authenticityCheck = retryCheck;
                } else {
                    modelResponse.setContent(buildToolAuthenticityFallback(retryCheck));
                    authenticityCheck = retryCheck;
                }
            }

            if (sources != null && !sources.isEmpty()) {
                List<Map<String, Object>> cited = knowledgeContextService.ensureCitations(modelResponse, sources);
                modelResponse.setSources(cited != null && !cited.isEmpty() ? cited : null);
            } else {
                modelResponse.setSources(null);
            }

            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs, agent, provider);
            knowledgeContextService.recordCitationsAsync(agent.getId(), conversation.getId(), assistantMessage.getId(),
                    modelResponse.getSources());
            knowledgeContextService.recordRetrievalOutcomeAsync(agent.getId(), conversation.getId(), assistantMessage.getId(),
                    effectiveContent(rewrittenContent, dto.getMessage()), sources, modelResponse.getSources());
            extractAdminPreferenceAsync(userId, conversation.getId(), userMessage, assistantMessage, agent, provider);
            extractSessionMemoryAsync(userId, conversation.getId(), userMessage, assistantMessage, agent, provider);
            updateConversationMessageCount(conversation.getId());
            if (runId == null) {
                runId = saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), effectiveContent(rewrittenContent, dto.getMessage()), modelResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason(), skillContext.getSnapshot());
            } else {
                updateRun(runId, assistantMessage.getId(), modelResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            }
            attachPendingArtifacts(runId, assistantMessage.getId());

            AgentMessageVo vo = new AgentMessageVo();
            BeanUtils.copyProperties(assistantMessage, vo);
            return vo;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (runId == null) {
                saveFailedRun(agent, provider, userId, conversation.getId(), userMessage.getId(), effectiveContent(rewrittenContent, dto.getMessage()), latencyMs, e);
            } else {
                updateRun(runId, userMessage.getId(), null, latencyMs, RUN_STATUS_FAILED, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 处理stream。
     */
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
        ModelProvider provider = getEnabledProvider(agent);
        applyThinkingConfig(dto, agent);
        long agentResolvedAt = System.currentTimeMillis();
        AgentConversation conversation = getOrCreateConversation(dto, userId, agent);
        stageRuntimeEmailSecrets(conversation.getId(), userId, dto);
        long conversationResolvedAt = System.currentTimeMillis();
        String rewrittenContent = rewriteUserMessage(conversation.getId(), dto.getMessage(), agent, provider);
        AgentMessage userMessage = saveUserMessage(conversation.getId(), dto.getMessage(), rewrittenContent,
                dto.getAttachmentContent(), dto.getAttachments(), agent, provider);
        long userPersistedAt = System.currentTimeMillis();
        String runId = null;

        log.info("流式请求开始: requestId={}, agent={}, model={}, thinking={}",
                dto.getRequestId(), agent.getId(), agent.getModel(), agent.getDefaultThinking());
        log.info("聊天接入耗时: requestId={}, agentProvider={}ms, conversation={}ms, userMessagePersist={}ms, conversationId={}",
                dto.getRequestId(), agentResolvedAt - startTime, conversationResolvedAt - agentResolvedAt,
                userPersistedAt - conversationResolvedAt, conversation.getId());

        try {
            long t0 = System.currentTimeMillis();
            callback.onStatus("preparing", "正在准备对话上下文");
            SkillRuntimeContext skillContext = resolveSkillContext(agent, dto, effectiveContent(rewrittenContent, dto.getMessage()), provider);
            long skillResolvedAt = System.currentTimeMillis();
            List<ModelChatMessage> context = buildContextWithSummary(agent, provider, conversation.getId(), userId);
            long contextBuiltAt = System.currentTimeMillis();
            applySkillPrompt(context, skillContext);
            callback.onStatus("retrieving", "正在检索资料");
            List<Map<String, Object>> sources = knowledgeContextService.enhance(
                    context, userId, conversation.getId(), agent.getId(), effectiveContent(rewrittenContent, dto.getMessage()), skillContext.getKnowledgeBaseIds(), dto.getRetrievalMode());
            long retrievalCompletedAt = System.currentTimeMillis();
            enforceSkillBudget(context, agent, provider, skillContext);
            conversationContextService.enforceBudget(context, agent, provider);
            long t1 = System.currentTimeMillis();
            log.info("聊天预处理耗时: requestId={}, conversationId={}, skill={}ms, historySummary={}ms, retrieval={}ms, budget={}ms, inputTokens={}, sources={}",
                    dto.getRequestId(), conversation.getId(), skillResolvedAt - t0, contextBuiltAt - skillResolvedAt,
                    retrievalCompletedAt - contextBuiltAt, t1 - retrievalCompletedAt,
                    conversationContextService.estimateContextTokens(context, agent.getModel()), sources == null ? 0 : sources.size());
            ChatLatencyMetrics.record("chat.skill", skillResolvedAt - t0);
            ChatLatencyMetrics.record("chat.history_summary", contextBuiltAt - skillResolvedAt);
            ChatLatencyMetrics.record("chat.retrieval", retrievalCompletedAt - contextBuiltAt);
            ChatLatencyMetrics.record("chat.pre_model", t1 - t0);

            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);
            request.setTools(agentToolWorkflow.getRequestTools(skillContext.getTools(),
                    effectiveContent(rewrittenContent, dto.getMessage()), skillContext.getRequiredToolIds()));

            // SSE 首个分片到达前即保存运行快照，避免连接中断时丢失实际授权上下文。
            runId = saveRun(agent, provider, userId, conversation.getId(), userMessage.getId(),
                    modelInputSnapshot(request), null, 0, RUN_STATUS_SUCCESS, null, skillContext.getSnapshot());

            ModelClient modelClient = modelClientFactory.getClient(provider);
            callback.onStatus("generating", "正在生成回答");
            // 推理未开启时，不转发reasoning chunks
            boolean thinkingEnabled = Boolean.TRUE.equals(agent.getDefaultThinking());
            ForwardingStreamCallback streamCallback = createStreamCallback(callback, conversation.getId(), thinkingEnabled);
            long modelStreamStartedAt = System.currentTimeMillis();
            ModelStreamResponse modelResponse = dispatchStream(modelClient, request, streamCallback, runId, 1);
            recordModelStreamLatency(streamCallback, modelStreamStartedAt);

            // 推理未开启时，过滤掉模型可能返回的reasoning_content和reasoning_tokens
            if (!thinkingEnabled && StringUtils.isBlank(modelResponse.getToolCalls())) {
                modelResponse.setReasoningContent(null);
                modelResponse.setReasoningTokens(null);
            }
            validateNonEmptyStreamResponse(modelResponse);

            // 当模型未提供token统计时（如Google Gemma流式响应），补充估算值
            fillDefaultTokens(modelResponse, context, effectiveContent(rewrittenContent, dto.getMessage()));

            log.info("流式请求完成: requestId={}, conversationId={}, total={}ms", dto.getRequestId(),
                    conversation.getId(), System.currentTimeMillis() - startTime);

            // 处理工具调用循环
            int iteration = 0;
            boolean toolCallAttempted = false;
            boolean toolCallSucceeded = false;
            ModelChatResponse chatResponse = toChatResponse(modelResponse);
            while (hasToolCalls(chatResponse) && iteration < MAX_TOOL_CALL_ITERATIONS) {
                if (runId == null) {
                    runId = saveRun(agent, provider, userId, conversation.getId(), userMessage.getId(), effectiveContent(rewrittenContent, dto.getMessage()), chatResponse, 0, RUN_STATUS_SUCCESS, null, skillContext.getSnapshot());
                }
                iteration++;
                toolCallAttempted = true;
                attachCitedSources(chatResponse, sources);
                AgentMessage assistantPrelude = saveAssistantPreludeIfPresent(
                        conversation.getId(), chatResponse, System.currentTimeMillis() - startTime, agent, provider);
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
                        doneResponse.setSources(assistantPrelude == null ? null : chatResponse.getSources());
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
                AgentMessage approval = agentToolWorkflow.createMcpApproval(conversation.getId(), chatResponse, agent, userId, runId, skillContext.getTools());
                if (approval != null) {
                    updateConversationMessageCount(conversation.getId());
                    AgentMessageVo approvalVo = new AgentMessageVo();
                    BeanUtils.copyProperties(approval, approvalVo);
                    updateRun(runId, approval.getId(), chatResponse, System.currentTimeMillis() - startTime,
                            RUN_STATUS_WAITING_USER, null);
                    if (!callback.isClosed()) {
                        ModelStreamResponse done = new ModelStreamResponse();
                        AgentMessage doneMessage = assistantPrelude != null ? assistantPrelude : approval;
                        done.setContent(doneMessage.getContent());
                        done.setWaitingUser(true);
                        callback.onQuestion(conversation.getId(), runId, approvalVo);
                        callback.onDone(conversation.getId(), doneMessage.getId(), done);
                    }
                    return;
                }
                long toolExecutionStartedAt = System.currentTimeMillis();
                List<ToolExecutionResult> toolResults = agentToolWorkflow.executeMcpCalls(chatResponse, agent, userId, runId, skillContext.getTools());
                saveToolResultMessages(conversation.getId(), toolResults, agent, provider);
                log.info("工具执行耗时: requestId={}, runId={}, duration={}ms, calls={}", dto.getRequestId(), runId,
                        System.currentTimeMillis() - toolExecutionStartedAt, toolResults.size());
                ChatLatencyMetrics.record("chat.tool_execution", System.currentTimeMillis() - toolExecutionStartedAt);
                toolCallSucceeded = toolCallSucceeded || hasSuccessfulToolResult(toolResults);

                addToolResultsToContext(context, chatResponse, toolResults);
                enforceSkillBudget(context, agent, provider, skillContext);
                chatRunService.updateSkillSnapshot(runId, skillContext.getSnapshot());
                conversationContextService.enforceBudget(context, agent, provider);

                // 继续调用模型（流式）
                request.setMessages(context);
                streamCallback = createStreamCallback(callback, conversation.getId(), thinkingEnabled);
                modelStreamStartedAt = System.currentTimeMillis();
                modelResponse = dispatchStream(modelClient, request, streamCallback, runId, iteration + 1);
                recordModelStreamLatency(streamCallback, modelStreamStartedAt);

                // 推理未开启时，过滤掉reasoning_content
                if (!thinkingEnabled && StringUtils.isBlank(modelResponse.getToolCalls())) {
                    modelResponse.setReasoningContent(null);
                }
                validateNonEmptyStreamResponse(modelResponse);

                fillDefaultTokens(modelResponse, context, effectiveContent(rewrittenContent, dto.getMessage()));
                chatResponse = toChatResponse(modelResponse);
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            ToolAuthenticityCheck authenticityCheck = checkToolAuthenticity(modelResponse.getContent(), toolCallAttempted, toolCallSucceeded);
            if (!authenticityCheck.isValid()) {
                log.warn("拦截疑似工具结果幻觉流式回答: conversationId={}, attempted={}, succeeded={}, reason={}",
                        conversation.getId(), toolCallAttempted, toolCallSucceeded, authenticityCheck.getReason());
                ModelChatResponse retryResponse = retryToolAuthenticity(modelClient, request, chatResponse, authenticityCheck, runId);
                ToolAuthenticityCheck retryCheck = checkToolAuthenticity(retryResponse.getContent(), false, false);
                if (retryCheck.isValid()) {
                    modelResponse = toStreamResponse(retryResponse, modelResponse);
                    chatResponse = retryResponse;
                    authenticityCheck = retryCheck;
                    if (!callback.isClosed()) {
                        callback.onMessage(conversation.getId(), "\n\n更正：" + retryResponse.getContent());
                    }
                } else {
                    modelResponse.setContent(buildToolAuthenticityFallback(retryCheck));
                    chatResponse.setContent(modelResponse.getContent());
                    authenticityCheck = retryCheck;
                }
            }
            if (sources != null && !sources.isEmpty()) {
                List<Map<String, Object>> cited = knowledgeContextService.ensureCitations(modelResponse, sources);
                modelResponse.setSources(cited != null && !cited.isEmpty() ? cited : null);
            } else {
                modelResponse.setSources(null);
            }


            long finalPersistStartedAt = System.currentTimeMillis();
            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs, agent, provider);
            knowledgeContextService.recordCitationsAsync(agent.getId(), conversation.getId(), assistantMessage.getId(),
                    modelResponse.getSources());
            knowledgeContextService.recordRetrievalOutcomeAsync(agent.getId(), conversation.getId(), assistantMessage.getId(),
                    effectiveContent(rewrittenContent, dto.getMessage()), sources, modelResponse.getSources());
            extractAdminPreferenceAsync(userId, conversation.getId(), userMessage, assistantMessage, agent, provider);
            extractSessionMemoryAsync(userId, conversation.getId(), userMessage, assistantMessage, agent, provider);
            updateConversationMessageCount(conversation.getId());
            if (runId == null) {
                runId = saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), effectiveContent(rewrittenContent, dto.getMessage()), chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason(), skillContext.getSnapshot());
            } else {
                updateRun(runId, assistantMessage.getId(), chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            }
            attachPendingArtifacts(runId, assistantMessage.getId());
            log.info("聊天最终落库耗时: requestId={}, runId={}, duration={}ms", dto.getRequestId(), runId,
                    System.currentTimeMillis() - finalPersistStartedAt);
            ChatLatencyMetrics.record("chat.final_persist", System.currentTimeMillis() - finalPersistStartedAt);
            modelResponse.setRunId(runId);
            if (!callback.isClosed()) {
                callback.onDone(conversation.getId(), assistantMessage.getId(), modelResponse);
            }
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (runId == null) {
                saveFailedRun(agent, provider, userId, conversation.getId(), userMessage.getId(), effectiveContent(rewrittenContent, dto.getMessage()), latencyMs, e);
            } else {
                updateRun(runId, userMessage.getId(), null, latencyMs, RUN_STATUS_FAILED, e.getMessage());
            }
            if (!callback.isClosed()) {
                callback.onError(resolveErrorCode(e), resolveErrorMessage(e));
            }
        }
    }

    /**
     * 处理streamReply。
     */
    private void streamReply(AgentChatDto dto, AgentStreamCallback callback) {
        String userId = resolveUserId(dto.getUserId());
        long startTime = System.currentTimeMillis();
        String runId = null;
        AgentConversation conversation = null;
        AgentDefinition agent = null;
        ModelProvider provider = null;
        AgentMessage answerMessage = null;
        String answerContent = null;
        ApprovalExecution approvalExecution = null;

        try {
            conversation = getOpenReplyConversation(dto.getConversationId(), userId, Boolean.TRUE.equals(dto.getTemporary()));
            AgentMessage question = getPendingInteraction(conversation.getId(), dto.getParentMessageId());

            answerContent = interactionReplyService.renderAnswerContent(question, dto.getAnswer());
            answerMessage = saveAnswerMessage(conversation.getId(), question.getId(), answerContent);
            markInteractionAnswered(question, dto.getAnswer(), System.currentTimeMillis());

            agent = getEnabledAgent(conversation.getAgentDefinitionId());
            provider = getEnabledProvider(agent);
            applyReplyThinkingConfig(dto, agent);
            boolean thinkingEnabled = Boolean.TRUE.equals(agent.getDefaultThinking());

            SkillRuntimeContext skillContext = resolveSkillContext(agent, dto, answerContent, provider);
            List<ModelChatMessage> context = buildContextWithSummary(agent, provider, conversation.getId(), userId);
            applySkillPrompt(context, skillContext);
            List<Map<String, Object>> sources = knowledgeContextService.enhance(
                    context, userId, conversation.getId(), agent.getId(), answerContent, skillContext.getKnowledgeBaseIds(), dto.getRetrievalMode());
            approvalExecution = agentToolWorkflow.executeApprovedMcpTool(question, dto.getAnswer(), agent, userId);
            if (approvalExecution != null) {
                runId = approvalExecution.getRunId();
                addToolResultsToContext(context, approvalExecution.getToolCallResponse(),
                        Collections.singletonList(approvalExecution.getResult()));
            }
            enforceSkillBudget(context, agent, provider, skillContext);
            conversationContextService.enforceBudget(context, agent, provider);
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);
            // A rejected approval is final for this continuation. Do not expose
            // MCP (or ask_user) again, otherwise the model can immediately ask
            // the same confirmation a second time.
            boolean approvalRejected = approvalExecution != null && !approvalExecution.getResult().isSuccess();
            request.setTools(approvalRejected ? Collections.<AgentTool>emptyList()
                    : agentToolWorkflow.getRequestTools(skillContext.getTools(), answerContent, skillContext.getRequiredToolIds()));

            // 回答分支同样在模型调用前固化 Skill 快照；已存在的审批续跑运行保留其原始快照。
            if (runId == null) {
                runId = saveRun(agent, provider, userId, conversation.getId(), answerMessage.getId(), modelInputSnapshot(request),
                        null, 0, RUN_STATUS_SUCCESS, null, skillContext.getSnapshot());
            }

            ModelClient modelClient = modelClientFactory.getClient(provider);
            ForwardingStreamCallback streamCallback = createStreamCallback(callback, dto.getConversationId(), thinkingEnabled);
            long modelStreamStartedAt = System.currentTimeMillis();
            ModelStreamResponse modelResponse = dispatchStream(modelClient, request, streamCallback, runId, 1);
            recordModelStreamLatency(streamCallback, modelStreamStartedAt);

            if (!thinkingEnabled && StringUtils.isBlank(modelResponse.getToolCalls())) {
                modelResponse.setReasoningContent(null);
                modelResponse.setReasoningTokens(null);
            }
            validateNonEmptyStreamResponse(modelResponse);
            fillDefaultTokens(modelResponse, context, answerContent);

            int iteration = 0;
            boolean toolCallAttempted = approvalExecution != null;
            boolean toolCallSucceeded = approvalExecution != null && approvalExecution.getResult().isSuccess();
            ModelChatResponse chatResponse = toChatResponse(modelResponse);
            while (hasToolCalls(chatResponse) && iteration < MAX_TOOL_CALL_ITERATIONS) {
                if (runId == null) {
                    runId = saveRun(agent, provider, userId, conversation.getId(), answerMessage.getId(), answerContent, chatResponse, 0, RUN_STATUS_SUCCESS, null, skillContext.getSnapshot());
                }
                iteration++;
                toolCallAttempted = true;
                attachCitedSources(chatResponse, sources);
                AgentMessage assistantPrelude = saveAssistantPreludeIfPresent(
                        conversation.getId(), chatResponse, System.currentTimeMillis() - startTime, agent, provider);
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
                        doneResponse.setSources(assistantPrelude == null ? null : chatResponse.getSources());
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

                AgentMessage approval = agentToolWorkflow.createMcpApproval(conversation.getId(), chatResponse, agent, userId, runId, skillContext.getTools());
                if (approval != null) {
                    updateConversationMessageCount(conversation.getId());
                    AgentMessageVo approvalVo = new AgentMessageVo();
                    BeanUtils.copyProperties(approval, approvalVo);
                    updateRun(runId, approval.getId(), chatResponse, System.currentTimeMillis() - startTime,
                            RUN_STATUS_WAITING_USER, null);
                    if (!callback.isClosed()) {
                        ModelStreamResponse done = new ModelStreamResponse();
                        AgentMessage doneMessage = assistantPrelude != null ? assistantPrelude : approval;
                        done.setContent(doneMessage.getContent());
                        done.setWaitingUser(true);
                        callback.onQuestion(conversation.getId(), runId, approvalVo);
                        callback.onDone(conversation.getId(), doneMessage.getId(), done);
                    }
                    return;
                }
                List<ToolExecutionResult> toolResults = agentToolWorkflow.executeMcpCalls(chatResponse, agent, userId, runId, skillContext.getTools());
                saveToolResultMessages(conversation.getId(), toolResults, agent, provider);
                toolCallSucceeded = toolCallSucceeded || hasSuccessfulToolResult(toolResults);
                addToolResultsToContext(context, chatResponse, toolResults);
                enforceSkillBudget(context, agent, provider, skillContext);
                chatRunService.updateSkillSnapshot(runId, skillContext.getSnapshot());
                conversationContextService.enforceBudget(context, agent, provider);

                request.setMessages(context);
                streamCallback = createStreamCallback(callback, dto.getConversationId(), thinkingEnabled);
                modelStreamStartedAt = System.currentTimeMillis();
                modelResponse = dispatchStream(modelClient, request, streamCallback, runId, iteration + 1);
                recordModelStreamLatency(streamCallback, modelStreamStartedAt);
                if (!thinkingEnabled && StringUtils.isBlank(modelResponse.getToolCalls())) {
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
                ModelChatResponse retryResponse = retryToolAuthenticity(modelClient, request, chatResponse, authenticityCheck, runId);
                ToolAuthenticityCheck retryCheck = checkToolAuthenticity(retryResponse.getContent(), false, false);
                if (retryCheck.isValid()) {
                    modelResponse = toStreamResponse(retryResponse, modelResponse);
                    chatResponse = retryResponse;
                    authenticityCheck = retryCheck;
                    if (!callback.isClosed()) {
                        callback.onMessage(conversation.getId(), "\n\n更正：" + retryResponse.getContent());
                    }
                } else {
                    modelResponse.setContent(buildToolAuthenticityFallback(retryCheck));
                    chatResponse.setContent(modelResponse.getContent());
                    authenticityCheck = retryCheck;
                }
            }

            if (authenticityCheck.isValid()) {
                List<Map<String, Object>> cited = knowledgeContextService.ensureCitations(modelResponse, sources);
                modelResponse.setSources(cited != null && !cited.isEmpty() ? cited : null);
            } else {
                modelResponse.setSources(null);
            }

            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs, agent, provider);
            knowledgeContextService.recordCitationsAsync(agent.getId(), conversation.getId(), assistantMessage.getId(),
                    modelResponse.getSources());
            knowledgeContextService.recordRetrievalOutcomeAsync(agent.getId(), conversation.getId(), assistantMessage.getId(),
                    answerContent, sources, modelResponse.getSources());
            extractAdminPreferenceAsync(userId, conversation.getId(), answerMessage, assistantMessage, agent, provider);
            extractSessionMemoryAsync(userId, conversation.getId(), answerMessage, assistantMessage, agent, provider);
            updateConversationMessageCount(conversation.getId());
            if (runId == null) {
                saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), answerContent, chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason(), skillContext.getSnapshot());
            } else {
                updateRun(runId, assistantMessage.getId(), chatResponse, latencyMs,
                        authenticityCheck.isValid() ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED,
                        authenticityCheck.isValid() ? null : authenticityCheck.getReason());
            }
            attachPendingArtifacts(runId, assistantMessage.getId());
            modelResponse.setRunId(runId);
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

    /**
     * 获取OpenReply会话。
     */
    private AgentConversation getOpenReplyConversation(String conversationId, String userId, boolean allowTemporary) {
        AgentConversation conversation = agentConversationService.getById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new ServerException(403, I18nUtils.getMessage("agent.conversation.access.denied"));
        }
        if (!Integer.valueOf(CONVERSATION_STATUS_OPEN).equals(conversation.getStatus())
                && !(allowTemporary && Integer.valueOf(2).equals(conversation.getStatus()))) {
            throw new ServerException(422, I18nUtils.getMessage("agent.conversation.closed"));
        }
        return conversation;
    }

    /**
     * 获取PendingInteraction。
     */
    private AgentMessage getPendingInteraction(String conversationId, String parentMessageId) {
        AgentMessage question = agentMessageService.getOne(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getId, parentMessageId)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getDeleted, false));
        if (question == null || !MESSAGE_TYPE_INTERACTION.equals(question.getMessageType())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.interaction.question.not.found"));
        }
        if (!INTERACTION_STATUS_PENDING.equals(question.getInteractionStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("agent.interaction.question.processed"));
        }
        if (question.getExpiresAt() != null && question.getExpiresAt() < System.currentTimeMillis()) {
            markInteractionStatus(question.getId(), "expired", null);
            throw new ServerException(409, I18nUtils.getMessage("agent.interaction.question.expired"));
        }
        return question;
    }

    /**
     * 校验Request。
     */
    private void validateRequest(AgentChatDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getAgentId()) || StringUtils.isBlank(dto.getMessage())) {
            throw new ServerException(400, I18nUtils.getMessage("agent.request.invalid"));
        }
    }

    /**
     * 校验StreamRequest。
     */
    private void validateStreamRequest(AgentChatDto dto) {
        if (dto == null) {
            throw new ServerException(400, I18nUtils.getMessage("agent.request.invalid"));
        }
        if (isInteractionReplyRequest(dto)) {
            if (StringUtils.isNotBlank(dto.getConversationId())
                    && StringUtils.isNotBlank(dto.getParentMessageId())
                    && dto.getAnswer() != null) {
                return;
            }
            throw new ServerException(400, I18nUtils.getMessage("agent.request.invalid"));
        }
        validateRequest(dto);
    }

    /**
     * 判断是否为InteractionReplyRequest。
     */
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
                throw new ServerException(400, I18nUtils.getMessage("agent.reasoning.effort.invalid"));
            }
            agent.setDefaultReasoningEffort(effort);
        }
    }

    /**
     * 处理applyReplyThinking配置。
     */
    private void applyReplyThinkingConfig(AgentChatDto dto, AgentDefinition agent) {
        agent.setDefaultThinking(Boolean.TRUE.equals(dto.getThinking()));
        if (StringUtils.isNotBlank(dto.getReasoningEffort())) {
            String effort = dto.getReasoningEffort().toLowerCase();
            if (!effort.equals("low") && !effort.equals("medium") && !effort.equals("high")) {
                throw new ServerException(400, I18nUtils.getMessage("agent.reasoning.effort.invalid"));
            }
            agent.setDefaultReasoningEffort(effort);
        }
    }

    /**
     * 获取当前用户Id。
     */
    private String getCurrentUserId(AgentChatDto dto) {
        // 优先使用DTO中传递的userId（适用于异步线程池场景）
        String userId = dto == null ? null : dto.getUserId();
        return resolveUserId(userId);
    }

    /**
     * 解析用户Id。
     */
    private String resolveUserId(String userId) {
        // 如果DTO中没有，则从CurrentUser获取（适用于同步调用场景）
        if (StringUtils.isBlank(userId)) {
            HashMap<String, String> currentUser = CurrentUser.getUser();
            userId = currentUser == null ? null : currentUser.get("userId");
        }
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, I18nUtils.getMessage("agent.authorization.required"));
        }
        return userId;
    }

    /**
     * 获取Enabled智能体。
     */
    @Override
    public AgentDefinition getEnabledAgent(String agentId) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.definition.not.found"));
        }
        if (!Integer.valueOf(AGENT_STATUS_ENABLED).equals(agent.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.definition.disabled"));
        }
        return agent;
    }

    /**
     * Product OpenAPI calls validate that the underlying Agent is still
     * enabled, then run the frozen configuration recorded at publish time.
     * Browser/admin calls intentionally continue to use the live definition.
     */
    private AgentDefinition resolveRuntimeAgent(AgentChatDto dto, AgentDefinition enabledAgent) {
        AgentDefinition snapshot = dto.getAgentSnapshot();
        if (snapshot == null) return enabledAgent;
        if (!Boolean.TRUE.equals(dto.getOpenApi()) || !StringUtils.equals(enabledAgent.getId(), snapshot.getId())
                || !StringUtils.equals(enabledAgent.getApplicationId(), snapshot.getApplicationId())) {
            throw new ServerException(422, "非法 Agent 快照");
        }
        return snapshot;
    }

    /**
     * 获取EnabledProvider。
     */
    private ModelProvider getEnabledProvider(AgentDefinition agent) {
        if (modelCatalogService == null || StringUtils.isBlank(agent.getModelId())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.model.provider.not.found"));
        }
        return modelCatalogService.resolveProvider(agent.getModelId(), "CHAT,MULTIMODAL");
    }

    /**
     * 获取Or创建会话。
     */
    private AgentConversation getOrCreateConversation(AgentChatDto dto, String userId, AgentDefinition agent) {
        if (StringUtils.isBlank(dto.getConversationId())) {
            AgentConversation conversation = new AgentConversation();
            conversation.setApplicationId(agent.getApplicationId());
            conversation.setUserId(userId);
            conversation.setAgentDefinitionId(agent.getId());
            conversation.setTitle(buildConversationTitle(dto.getMessage()));
            conversation.setMessageCount(0);
            conversation.setStatus(Boolean.TRUE.equals(dto.getTemporary()) ? 2 : CONVERSATION_STATUS_OPEN);
            conversation.setToolApprovalPolicy(normalizeToolApprovalPolicy(dto.getToolApprovalPolicy()));
            agentConversationService.save(conversation);
            return conversation;
        }

        AgentConversation conversation = agentConversationService.getById(dto.getConversationId());
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.conversation.not.found"));
        }
        if (!userId.equals(conversation.getUserId()) && !Boolean.TRUE.equals(dto.getOpenApi())) {
            throw new ServerException(403, I18nUtils.getMessage("agent.conversation.access.denied"));
        }
        if (!agent.getId().equals(conversation.getAgentDefinitionId())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.conversation.agent.mismatch"));
        }
        if (!StringUtils.equals(agent.getApplicationId(), conversation.getApplicationId())) {
            throw new ServerException(403, "会话不属于当前业务应用空间");
        }
        if (!Integer.valueOf(CONVERSATION_STATUS_OPEN).equals(conversation.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.conversation.closed"));
        }
        return conversation;
    }

    /**
     * 规范化ToolApprovalPolicy。
     */
    private String normalizeToolApprovalPolicy(String policy) {
        return "risky".equals(policy) || "never".equals(policy) ? policy : "ask";
    }

    /**
     * 构建会话Title。
     */
    private String buildConversationTitle(String message) {
        String title = StringUtils.defaultString(message).trim();
        return title.length() > 50 ? title.substring(0, 50) : title;
    }

    /**
     * 保存用户消息。
     */
    private AgentMessage saveUserMessage(String conversationId, String content, String rewrittenContent,
                                          String attachmentContent, String attachments,
                                          AgentDefinition agent, ModelProvider provider) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setMessageType(MESSAGE_TYPE_CHAT);
        message.setContent(content);
        message.setRewrittenContent(rewrittenContent);
        message.setAttachmentContent(attachmentContent);
        message.setAttachments(attachments);
        message.setContextTokens(conversationContextService.estimateTokens(
                effectiveContent(rewrittenContent, content), agent == null ? null : agent.getModel()));
        message.setContextBudgetTokens(conversationContextService.getInputTokenBudget(agent, provider));
        agentMessageService.save(message);

        // 更新缓存：添加用户消息
        updateContextCache(conversationId, new ModelChatMessage("user", effectiveContent(rewrittenContent, content)));

        return message;
    }

    /**
     * 保存Answer消息。
     */
    private AgentMessage saveAnswerMessage(String conversationId, String parentMessageId, String content) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setMessageType(MESSAGE_TYPE_ANSWER);
        message.setParentMessageId(parentMessageId);
        message.setContent(content);
        message.setRewrittenContent(content);
        agentMessageService.save(message);
        updateContextCache(conversationId, new ModelChatMessage("user", content));
        return message;
    }

    /**
     * 处理rewrite用户消息。
     */
    private String rewriteUserMessage(String conversationId, String originalContent,
                                      AgentDefinition agent, ModelProvider provider) {
        if (!queryRewriteEnabled) {
            return null;
        }
        try {
            List<ModelChatMessage> history = conversationContextService.buildRewriteHistory(conversationId);
            return queryRewriteService.rewrite(history, originalContent, agent, provider).getRewrittenContent();
        } catch (Exception e) {
            log.warn("查询重写不可用，使用原始消息继续: conversationId={}", conversationId, e);
            return null;
        }
    }

    /**
     * 处理effectiveContent。
     */
    private String effectiveContent(String rewrittenContent, String originalContent) {
        return StringUtils.defaultIfBlank(rewrittenContent, originalContent);
    }

    /**
     * 处理markInteraction状态。
     */
    private void markInteractionStatus(String messageId, String status, Long answeredAt) {
        AgentMessage update = new AgentMessage();
        update.setId(messageId);
        update.setInteractionStatus(status);
        if (answeredAt != null) {
            update.setAnsweredAt(answeredAt);
        }
        agentMessageService.updateById(update);
    }

    /**
     * 处理markInteractionAnswered。
     */
    private void markInteractionAnswered(AgentMessage question, Map<String, Object> answer, Long answeredAt) {
        AgentMessage update = new AgentMessage();
        update.setId(question.getId());
        update.setInteractionStatus(INTERACTION_STATUS_ANSWERED);
        update.setAnsweredAt(answeredAt);
        update.setQuestionConfig(interactionReplyService.buildAnsweredQuestionConfig(question, answer, answeredAt));
        agentMessageService.updateById(update);
    }

    /**
     * 保存Assistant消息。
     */
    private AgentMessage saveAssistantMessage(String conversationId, ModelChatResponse modelResponse, long latencyMs) {
        return saveAssistantMessage(conversationId, modelResponse, latencyMs, null, null);
    }

    private AgentMessage saveAssistantMessage(String conversationId, ModelChatResponse modelResponse, long latencyMs,
                                              AgentDefinition agent, ModelProvider provider) {
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
        populateAssistantContextMetrics(message, modelResponse.getContent(), agent, provider);
        if (modelResponse.getSources() != null && !modelResponse.getSources().isEmpty()) {
            message.setCitations(JSON.toJSONString(modelResponse.getSources()));
        }
        message.setLatencyMs((int) latencyMs);
        agentMessageService.save(message);

        // 更新缓存：添加助手消息
        updateContextCache(conversationId, new ModelChatMessage("assistant", modelResponse.getContent()));

        return message;
    }

    /**
     * 保存Assistant消息。
     */
    private AgentMessage saveAssistantMessage(String conversationId, ModelStreamResponse modelResponse, long latencyMs) {
        return saveAssistantMessage(conversationId, modelResponse, latencyMs, null, null);
    }

    private AgentMessage saveAssistantMessage(String conversationId, ModelStreamResponse modelResponse, long latencyMs,
                                              AgentDefinition agent, ModelProvider provider) {
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
        populateAssistantContextMetrics(message, modelResponse.getContent(), agent, provider);
        if (modelResponse.getSources() != null && !modelResponse.getSources().isEmpty()) {
            message.setCitations(JSON.toJSONString(modelResponse.getSources()));
        }
        message.setLatencyMs((int) latencyMs);
        agentMessageService.save(message);

        // 更新缓存：添加助手消息
        updateContextCache(conversationId, new ModelChatMessage("assistant", modelResponse.getContent()));

        return message;
    }

    /**
     * 保存AssistantPreludeIfPresent。
     */
    private AgentMessage saveAssistantPreludeIfPresent(String conversationId, ModelChatResponse response, long latencyMs) {
        return saveAssistantPreludeIfPresent(conversationId, response, latencyMs, null, null);
    }

    private AgentMessage saveAssistantPreludeIfPresent(String conversationId, ModelChatResponse response, long latencyMs,
                                                       AgentDefinition agent, ModelProvider provider) {
        if (response == null || (StringUtils.isBlank(response.getContent()) && StringUtils.isBlank(response.getToolCalls()))) {
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
        prelude.setSources(response.getSources());
        prelude.setToolCalls(response.getToolCalls());
        AgentMessage message = saveAssistantMessage(conversationId, prelude, latencyMs, agent, provider);
        message.setMessageType(MESSAGE_TYPE_TOOL_CALL);
        agentMessageService.updateById(message);
        return message;
    }

    /**
     * Persists tool outputs as first-class conversation messages.  The model receives a
     * compact copy in the current turn, while this record retains the complete execution
     * envelope for replay and audit without mixing it into assistant prose.
     */
    private void saveToolResultMessages(String conversationId, List<ToolExecutionResult> results,
                                        AgentDefinition agent, ModelProvider provider) {
        if (results == null || results.isEmpty()) return;
        for (ToolExecutionResult result : results) {
            if (result == null) continue;
            AgentMessage message = new AgentMessage();
            message.setConversationId(conversationId);
            message.setRole("tool");
            message.setMessageType(MESSAGE_TYPE_TOOL_RESULT);
            message.setToolCallId(result.getToolCallId());
            // content is the exact tool payload used for protocol replay. toolResult retains
            // status, latency, raw response and error metadata independently for the UI/audit.
            message.setContent(StringUtils.defaultIfBlank(result.getContent(), result.getErrorMsg()));
            message.setToolResult(JSON.toJSONString(result));
            message.setLatencyMs(result.getLatencyMs());
            message.setContextTokens(conversationContextService.estimateTokens(message.getContent(),
                    agent == null ? null : agent.getModel()));
            message.setContextBudgetTokens(conversationContextService.getInputTokenBudget(agent, provider));
            agentMessageService.save(message);
            updateContextCache(conversationId, new ModelChatMessage("tool", message.getContent(), null, result.getToolCallId()));
        }
    }

    /**
     * 工具调用前输出的正文同样是用户可见的正式回答；在暂停到 ask_user 前，必须冻结
     * 与该轮检索编号一致的引用快照，避免前端看到孤立的 【n】 标记。
     */
    private void attachCitedSources(ModelChatResponse response, List<Map<String, Object>> sources) {
        if (response == null) return;
        if (sources == null || sources.isEmpty()) {
            response.setSources(null);
            return;
        }
        List<Map<String, Object>> cited = knowledgeContextService.ensureCitations(response, sources);
        response.setSources(cited == null || cited.isEmpty() ? null : cited);
    }

    /**
     * 处理to对话Response。
     */
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

    /**
     * 校验NonEmptyStreamResponse。
     */
    private void validateNonEmptyStreamResponse(ModelStreamResponse response) {
        if (response == null || (StringUtils.isBlank(response.getContent())
                && StringUtils.isBlank(response.getReasoningContent())
                && StringUtils.isBlank(response.getToolCalls()))) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.empty"));
        }
    }

    /**
     * 创建实时转发回调，保持流式消息的即时展示。
     */
    private ForwardingStreamCallback createStreamCallback(final AgentStreamCallback callback,
                                                          final String conversationId,
                                                          final boolean thinkingEnabled) {
        return new ForwardingStreamCallback(callback, conversationId, thinkingEnabled);
    }

    /**
     * 表示ForwardingStream回调。
     */
    private static class ForwardingStreamCallback implements ModelStreamCallback {
        private final AgentStreamCallback callback;
        private final String conversationId;
        private final boolean thinkingEnabled;
        private final long modelRequestStartedAt;
        private boolean firstMessageLogged;
        private long firstMessageAt;

        /**
         * 创建 {@code ForwardingStreamCallback} 实例。
         */
        private ForwardingStreamCallback(AgentStreamCallback callback, String conversationId, boolean thinkingEnabled) {
            this.callback = callback;
            this.conversationId = conversationId;
            this.thinkingEnabled = thinkingEnabled;
            this.modelRequestStartedAt = System.currentTimeMillis();
        }

        /**
         * 处理on消息。
         */
        @Override
        public void onMessage(String chunk) {
            if (!firstMessageLogged) {
                firstMessageLogged = true;
                firstMessageAt = System.currentTimeMillis();
                log.info("模型首字延迟: {}ms, conversationId={}",
                        System.currentTimeMillis() - modelRequestStartedAt, conversationId);
                ChatLatencyMetrics.record("chat.model_ttft", System.currentTimeMillis() - modelRequestStartedAt);
            }
            if (!callback.isClosed()) {
                callback.onMessage(conversationId, chunk);
            }
        }

        /**
         * 处理onReasoning。
         */
        @Override
        public void onReasoning(String chunk) {
            if (thinkingEnabled && !callback.isClosed()) {
                callback.onReasoning(conversationId, chunk);
            }
        }

        /**
         * 处理onToolCall。
         */
        @Override
        public void onToolCall(String toolCallJson) {
            if (!callback.isClosed()) {
                callback.onToolCall(conversationId, toolCallJson);
            }
        }

        /**
         * 判断是否为Closed。
         */
        @Override
        public boolean isClosed() {
            return callback.isClosed();
        }

        /**
         * 获取First消息At。
         */
        private long getFirstMessageAt() {
            return firstMessageAt;
        }

    }

    /**
     * 更新会话消息统计。
     */
    private void updateConversationMessageCount(String conversationId) {
        AgentConversation update = new AgentConversation();
        update.setId(conversationId);
        update.setMessageCount(null); // 避免覆盖
        agentConversationService.update(null, Wrappers.lambdaUpdate(AgentConversation.class)
                .eq(AgentConversation::getId, conversationId)
                .setSql("message_count = message_count + 1"));
    }

    /**
     * 保存Failed运行。
     */
    private void saveFailedRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                               String messageId, String input, long latencyMs, RuntimeException e) {
        chatRunService.saveFailure(agent, provider, userId, conversationId, messageId, input, latencyMs, e);
    }

    /**
     * 保存运行。
     */
    private String saveRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                           String messageId, String input, ModelChatResponse response, long latencyMs,
                           Integer status, String errorMsg) {
        return chatRunService.create(agent, provider, userId, conversationId, messageId, input,
                response, latencyMs, status, errorMsg);
    }

    /**
     * Persist exactly the prompt context provided to the model, without provider credentials.
     */
    private String modelInputSnapshot(ModelChatRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("model", StringUtils.defaultIfBlank(request.getModel(), request.getAgent() == null ? null : request.getAgent().getModel()));
        snapshot.put("messages", request.getMessages());
        snapshot.put("tools", request.getTools());
        snapshot.put("toolChoice", request.getToolChoice());
        snapshot.put("toolChoiceName", request.getToolChoiceName());
        snapshot.put("temperature", request.getTemperature());
        snapshot.put("topP", request.getTopP());
        snapshot.put("maxCompletionTokens", request.getMaxCompletionTokens());
        snapshot.put("reasoningEffort", request.getReasoningEffort());
        return JSON.toJSONString(snapshot);
    }

    /**
     * 处理record模型StreamLatency。
     */
    private void recordModelStreamLatency(ForwardingStreamCallback callback, long startedAt) {
        long completedAt = System.currentTimeMillis();
        ChatLatencyMetrics.record("chat.model_stream", completedAt - startedAt);
        if (callback.getFirstMessageAt() > 0) {
            ChatLatencyMetrics.record("chat.model_generation", completedAt - callback.getFirstMessageAt());
        }
    }

    /**
     * 保存运行。
     */
    private String saveRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                           String messageId, String input, ModelChatResponse response, long latencyMs,
                           Integer status, String errorMsg, String skillSnapshot) {
        String runId = chatRunService.create(agent, provider, userId, conversationId, messageId, input,
                response, latencyMs, status, errorMsg, snapshotWithToolApprovalPolicy(conversationId, skillSnapshot));
        if (runtimeEmailCredentialStore != null) {
            runtimeEmailCredentialStore.bindPending(runId, conversationId, userId);
        }
        return runId;
    }

    private void stageRuntimeEmailSecrets(String conversationId, String userId, AgentChatDto dto) {
        if (runtimeEmailCredentialStore == null || dto == null || dto.getRuntimeSecrets() == null
                || dto.getRuntimeSecrets().isEmpty()) {
            return;
        }
        runtimeEmailCredentialStore.putPending(conversationId, userId, dto.getRuntimeSecrets());
    }

    /**
     * 处理snapshotWithToolApprovalPolicy。
     */
    private String snapshotWithToolApprovalPolicy(String conversationId, String skillSnapshot) {
        JSONObject snapshot = StringUtils.isBlank(skillSnapshot) ? new JSONObject() : JSONObject.parseObject(skillSnapshot);
        AgentConversation conversation = agentConversationService.getById(conversationId);
        String policy = conversation == null ? "ask" : normalizeToolApprovalPolicy(conversation.getToolApprovalPolicy());
        snapshot.put("toolApprovalPolicy", policy);
        return snapshot.toJSONString();
    }

    /**
     * 更新运行。
     */
    private void updateRun(String runId, String messageId, ModelChatResponse response, long latencyMs,
                           Integer status, String errorMsg) {
        chatRunService.update(runId, messageId, response, latencyMs, status, errorMsg);
    }

    /**
     * Reconciles files that finished while the Agent was still generating its final reply.
     */
    private void attachPendingArtifacts(String runId, String messageId) {
        if (artifactExecutionService != null && StringUtils.isNoneBlank(runId, messageId)) {
            artifactExecutionService.attachPendingArtifacts(runId, messageId);
        }
    }

    /**
     * 处理truncate。
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 解析ErrorCode。
     */
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

    /**
     * 解析Error消息。
     */
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
        int promptTokens = conversationContextService.estimateContextTokens(context);
        response.setPromptTokens(promptTokens);

        // 估算completion tokens（基于输出内容）
        int completionTokens = conversationContextService.estimateTokens(response.getContent());
        response.setCompletionTokens(completionTokens);

        // 设置total tokens
        response.setTotalTokens(promptTokens + completionTokens);
    }

    /**
     * 更新会话上下文缓存。
     * 在保存新消息后调用，将新消息追加到缓存中的context。
     */
    private void updateContextCache(String conversationId, ModelChatMessage newMessage) {
        conversationContextService.append(conversationId, newMessage);
    }

    /**
     * 构建带摘要的对话上下文。
     * 当对话超过阈值时，使用摘要+最近消息的模式。
     */
    private List<ModelChatMessage> buildContextWithSummary(AgentDefinition agent, ModelProvider provider,
                                                           String conversationId, String userId) {
        List<ModelChatMessage> context = conversationContextService.buildWithSummary(agent, provider, conversationId);
        conversationContextService.injectSessionMemory(context, conversationId, userId, agent.getId());
        return context;
    }

    /**
     * 用冻结后的 Skill 指令替换历史中的 Agent 系统提示词，避免同一请求出现两套策略。
     */
    private void applySkillPrompt(List<ModelChatMessage> context, SkillRuntimeContext skillContext) {
        if (context == null || skillContext == null) return;
        List<String> messages = skillContext.getSystemMessages();
        if (messages == null || messages.isEmpty()) {
            messages = StringUtils.isBlank(skillContext.getSystemPrompt())
                    ? java.util.Collections.<String>emptyList()
                    : java.util.Collections.singletonList(skillContext.getSystemPrompt());
        }
        if (!context.isEmpty() && "system".equals(context.get(0).getRole())) context.remove(0);
        for (int i = messages.size() - 1; i >= 0; i--) context.add(0, new ModelChatMessage("system", messages.get(i)));
    }

    /**
     * Skill 已装配时，先拒绝超预算请求，再执行普通历史上下文裁剪，确保 Skill 指令不被截断。
     */
    private void enforceSkillBudget(List<ModelChatMessage> context, AgentDefinition agent,
                                    ModelProvider provider, SkillRuntimeContext skillContext) {
        if (skillContext == null || !skillContext.isInstalled()) return;
        int budget = conversationContextService.getInputTokenBudget(agent, provider);
        int promptTokens = conversationContextService.estimateTokens(
                String.join("\n", skillContext.getSystemMessages()), agent.getModel());
        int contextTokens = conversationContextService.estimateContextTokens(context, agent.getModel());
        skillContext.recordBudget(budget, promptTokens, contextTokens);
        if (promptTokens > budget) {
            throw new IllegalArgumentException("Skill context exceeds model input budget: " + promptTokens + "/" + budget + " tokens");
        }
    }

    /**
     * 解析SkillContext。
     */
    private SkillRuntimeContext resolveSkillContext(AgentDefinition agent, AgentChatDto dto, String routingQuery, ModelProvider provider) {
        if (skillContextService != null) return skillContextService.resolve(agent, dto, routingQuery, provider);
        SkillRuntimeContext context = new SkillRuntimeContext();
        String prompt = StringUtils.defaultString(agent.getSystemPrompt());
        if (capabilityIndexService != null) prompt += capabilityIndexService.buildIndex(agent.getId(), null);
        context.setSystemPrompt(prompt);
        context.setTools(agentToolWorkflow.getBoundTools(agent.getId()));
        context.setSnapshot("{\"installed\":false}");
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

    /**
     * 判断是否拥有SuccessfulTool结果。
     */
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

    /**
     * 新增ToolResultsToContext。
     */
    private void addToolResultsToContext(List<ModelChatMessage> context,
                                         ModelChatResponse response,
                                         List<ToolExecutionResult> toolResults) {
        if (context == null || response == null || toolResults == null || toolResults.isEmpty()) {
            return;
        }
        // DeepSeek thinking mode requires the prior reasoning_content to be replayed
        // together with the assistant tool call before a tool result can be continued.
        context.add(new ModelChatMessage("assistant", response.getContent(), response.getToolCalls(), null,
                response.getReasoningContent()));
        Map<String, String> toolNameByCallId = parseToolNameByCallId(response.getToolCalls());
        for (ToolExecutionResult result : toolResults) {
            if (result == null) {
                continue;
            }
            String toolContent = result.isSuccess()
                    ? compactToolContext(result.getContent())
                    : buildToolRetryInstruction(toolNameByCallId.get(result.getToolCallId()), result);
            if (toolContent == null) {
                toolContent = result.isSuccess() ? "" : "工具执行失败";
            }
            log.info("工具结果添加到上下文: toolCallId={}, success={}, originalChars={}, contextChars={}",
                    result.getToolCallId(), result.isSuccess(), StringUtils.length(result.getContent()), StringUtils.length(toolContent));
            context.add(new ModelChatMessage("tool", toolContent, null, result.getToolCallId()));
        }
    }

    /**
     * 处理compactToolContext。
     */
    private String compactToolContext(String content) {
        if (StringUtils.length(content) <= MAX_TOOL_CONTEXT_CHARS) {
            return content;
        }
        String compactJson = compactJsonToolContext(content);
        if (compactJson != null) {
            return compactJson;
        }
        String compactLines = compactLineToolContext(content);
        if (compactLines != null) {
            return compactLines;
        }
        int headLength = MAX_TOOL_CONTEXT_CHARS * 3 / 4;
        int tailLength = MAX_TOOL_CONTEXT_CHARS - headLength;
        return "[工具返回内容过长，已从 " + content.length() + " 字符压缩为首尾片段；"
                + "如需完整数据，请使用更精确的筛选参数再次调用工具]\n"
                + content.substring(0, headLength)
                + "\n...[中间 " + (content.length() - headLength - tailLength) + " 字符已省略]...\n"
                + content.substring(content.length() - tailLength);
    }

    private String compactJsonToolContext(String content) {
        try {
            Object parsed = JSON.parse(content);
            if (parsed instanceof JSONObject) {
                JSONObject object = (JSONObject) parsed;
                JSONObject compact = new JSONObject();
                for (String key : object.keySet()) {
                    if (!"debug".equalsIgnoreCase(key) && !"metadata".equalsIgnoreCase(key)
                            && !"trace".equalsIgnoreCase(key) && !"logs".equalsIgnoreCase(key)) {
                        compact.put(key, object.get(key));
                    }
                }
                String result = "[工具 JSON 结果已移除调试字段]\n" + compact.toJSONString();
                return result.length() <= MAX_TOOL_CONTEXT_CHARS ? result : null;
            }
            if (parsed instanceof JSONArray) {
                JSONArray values = (JSONArray) parsed;
                JSONArray sample = new JSONArray();
                for (int i = 0; i < Math.min(values.size(), 10); i++) sample.add(values.get(i));
                String result = "[工具列表共 " + values.size() + " 项，仅保留前 " + sample.size() + " 项]\n"
                        + sample.toJSONString();
                return result.length() <= MAX_TOOL_CONTEXT_CHARS ? result : null;
            }
        } catch (Exception ignored) {
            // Not JSON; fall through to line-oriented compaction.
        }
        return null;
    }

    private String compactLineToolContext(String content) {
        String[] lines = content.split("\\r?\\n");
        if (lines.length <= 80) return null;
        StringBuilder compact = new StringBuilder("[工具输出共 ").append(lines.length)
                .append(" 行，仅保留首尾关键行]\n");
        for (int i = 0; i < 40; i++) compact.append(lines[i]).append('\n');
        compact.append("...[").append(lines.length - 80).append(" 行已省略]...\n");
        for (int i = lines.length - 40; i < lines.length; i++) compact.append(lines[i]).append('\n');
        return compact.toString();
    }

    /**
     * 解析ToolName按CallId。
     */
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

    /**
     * 构建Tool重试Instruction。
     */
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

    /**
     * 处理extract管理员偏好Async。
     */
    private void extractAdminPreferenceAsync(String userId,
                                             String conversationId,
                                             AgentMessage userMessage,
                                             AgentMessage assistantMessage,
                                             AgentDefinition agent,
                                             ModelProvider provider) {
        adminPreferenceExtractionService.extractAsync(userId, conversationId, userMessage, assistantMessage, agent, provider);
    }

    /**
     * 处理extract会话MemoryAsync。
     */
    private void extractSessionMemoryAsync(String userId,
                                           String conversationId,
                                           AgentMessage userMessage,
                                           AgentMessage assistantMessage,
                                           AgentDefinition agent,
                                           ModelProvider provider) {
        if (sessionMemoryExtractionService == null) {
            return;
        }
        sessionMemoryExtractionService.extractAsync(userId, conversationId, userMessage, assistantMessage, agent, provider);
    }


    /**
     * 重试Ask用户WhenPlainQuestion。
     */
    private ModelChatResponse retryAskUserWhenPlainQuestion(ModelChatResponse response,
                                                             ModelClient modelClient,
                                                             ModelChatRequest request,
                                                             String runId, int attemptNo) {
        if (response == null || hasToolCalls(response) || !looksLikeUserQuestion(response.getContent())) {
            return response;
        }
        log.warn("交互式模式下模型返回了普通问句，强制重试 ask_user: content={}", truncate(response.getContent(), 200));
        try {
            request.setToolChoiceName(ASK_USER_TOOL_NAME);
            return dispatchChat(modelClient, request, runId, attemptNo);
        } finally {
            request.setToolChoiceName(null);
        }
    }

    /**
     * 处理looksLike用户Question。
     */
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

    /**
     * 处理InternalToolCall。
     */
    private ToolResult handleInternalToolCall(String conversationId, ModelChatResponse response, long latencyMs) {
        try {
            ToolResult result = agentToolWorkflow.executeInternalCall(conversationId, response);
            if (result != null && StringUtils.isNotBlank(result.getContextContent())) {
                updateContextCache(conversationId, new ModelChatMessage("assistant", result.getContextContent()));
            }
            return result;
        } catch (ServerException e) {
            log.warn("内建工具参数不合法，降级为普通助手消息: conversationId={}, reason={}", conversationId, e.getMessage());
            ModelChatResponse fallback = new ModelChatResponse();
            String question = agentToolWorkflow.extractQuestionText(response);
            fallback.setContent(StringUtils.defaultIfBlank(question, "请补充必要信息后继续。"));
            return ToolResult.waitingUser(saveAssistantMessage(conversationId, fallback, latencyMs), null);
        }
    }

    /**
     * 判断是否拥有InternalToolCall。
     */
    private boolean hasInternalToolCall(ModelChatResponse response) {
        return agentToolWorkflow.hasInternalCall(response);
    }

    /**
     * 检查ToolAuthenticity。
     */
    private ToolAuthenticityCheck checkToolAuthenticity(String content, boolean toolCallAttempted, boolean toolCallSucceeded) {
        if (toolCallAttempted && !toolCallSucceeded) {
            return ToolAuthenticityCheck.invalid("工具调用已触发但没有成功执行记录");
        }
        if (!toolCallSucceeded && claimsToolBackedResult(content)) {
            return ToolAuthenticityCheck.invalid("模型声称使用了工具或接口，但本轮没有成功工具执行记录");
        }
        return ToolAuthenticityCheck.valid();
    }

    /**
     * 对疑似伪造工具结果的回复进行一次无工具重试。
     * 原始模型回复不会被改写，重试请求仅增加约束提示并移除工具定义。
     */
    private ModelChatResponse retryToolAuthenticity(ModelClient modelClient, ModelChatRequest request,
                                                     ModelChatResponse originalResponse,
                                                     ToolAuthenticityCheck check, String runId) {
        List<ModelChatMessage> originalMessages = request.getMessages();
        List<AgentTool> originalTools = request.getTools();
        String originalToolChoice = request.getToolChoiceName();
        List<ModelChatMessage> retryMessages = new ArrayList<ModelChatMessage>(originalMessages);
        retryMessages.add(new ModelChatMessage("system", "上一版回复包含未经证实的工具或接口结果。"
                + "请重新回答：不得声称调用过工具、接口或获得其结果；只能依据已提供的可信上下文。"
                + "如确需外部数据，请明确说明无法确认。原因：" + check.getReason()));
        try {
            request.setMessages(retryMessages);
            request.setTools(Collections.<AgentTool>emptyList());
            request.setToolChoiceName(null);
            ModelChatResponse retryResponse = dispatchChat(modelClient, request, runId, 99);
            return retryResponse == null ? originalResponse : retryResponse;
        } catch (Exception e) {
            log.warn("工具结果幻觉重试失败，将执行安全降级: reason={}", e.getMessage());
            return originalResponse;
        } finally {
            request.setMessages(originalMessages);
            request.setTools(originalTools);
            request.setToolChoiceName(originalToolChoice);
        }
    }

    /** Dispatch a normal response while preserving the exact input assembled for this call. */
    private ModelChatResponse dispatchChat(ModelClient client, ModelChatRequest request, String runId, int attemptNo) {
        conversationContextService.removeOrphanedToolMessages(request.getMessages());
AgentRunContextMetric preliminary = contextMetricService == null ? null
                : contextMetricService.recordPreliminary(runId, attemptNo, request.getMessages(),
                        request.getTools(), request.getAgent(), request.getProvider());
        ModelChatResponse response = client.chat(request);
        if (contextMetricService != null) contextMetricService.recordFinal(preliminary, response);
        return response;
    }

    /** Dispatch a stream while persisting both its estimated and provider-reported input usage. */
    private ModelStreamResponse dispatchStream(ModelClient client, ModelChatRequest request,
                                               ModelStreamCallback callback, String runId, int attemptNo) {
        conversationContextService.removeOrphanedToolMessages(request.getMessages());
AgentRunContextMetric preliminary = contextMetricService == null ? null
                : contextMetricService.recordPreliminary(runId, attemptNo, request.getMessages(),
                        request.getTools(), request.getAgent(), request.getProvider());
        ModelStreamResponse response = client.stream(request, callback);
        if (contextMetricService != null) contextMetricService.recordFinal(preliminary, response);
        return response;
    }

    private void populateAssistantContextMetrics(AgentMessage message, String content,
                                                 AgentDefinition agent, ModelProvider provider) {
        message.setContextTokens(conversationContextService.estimateTokens(content,
                agent == null ? null : agent.getModel()));
        if (agent != null || provider != null) {
            message.setContextBudgetTokens(conversationContextService.getInputTokenBudget(agent, provider));
        }
    }

    /**
     * 将非流式重试结果写回流式最终响应，保留已收集的引用来源。
     */
    private ModelStreamResponse toStreamResponse(ModelChatResponse retryResponse, ModelStreamResponse streamResponse) {
        streamResponse.setContent(retryResponse.getContent());
        streamResponse.setModel(retryResponse.getModel());
        streamResponse.setToolCalls(retryResponse.getToolCalls());
        streamResponse.setPromptTokens(retryResponse.getPromptTokens());
        streamResponse.setCompletionTokens(retryResponse.getCompletionTokens());
        streamResponse.setTotalTokens(retryResponse.getTotalTokens());
        streamResponse.setCachedPromptTokens(retryResponse.getCachedPromptTokens());
        streamResponse.setUncachedPromptTokens(retryResponse.getUncachedPromptTokens());
        streamResponse.setPromptCacheHitRate(retryResponse.getPromptCacheHitRate());
        return streamResponse;
    }

    /**
     * 处理claimsToolBacked结果。
     */
    private boolean claimsToolBackedResult(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String normalized = content.toLowerCase();
        String[] explicitClaims = new String[]{
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

    /**
     * 构建ToolAuthenticityFallback。
     */
    private String buildToolAuthenticityFallback(ToolAuthenticityCheck check) {
        return "工具调用未获得可信结果，已阻止生成可能不准确的工具结果。"
                + "请稍后重试，或检查工具配置。原因：" + check.getReason();
    }

    /**
     * 工具调用信息
     */

    private static class ToolAuthenticityCheck {
        private final boolean valid;
        private final String reason;

        /**
         * 创建 {@code ToolAuthenticityCheck} 实例。
         */
        private ToolAuthenticityCheck(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        /**
         * 处理valid。
         */
        static ToolAuthenticityCheck valid() {
            return new ToolAuthenticityCheck(true, null);
        }

        /**
         * 处理invalid。
         */
        static ToolAuthenticityCheck invalid(String reason) {
            return new ToolAuthenticityCheck(false, reason);
        }

        /**
         * 判断是否为Valid。
         */
        boolean isValid() {
            return valid;
        }

        /**
         * 获取Reason。
         */
        String getReason() {
            return reason;
        }
    }
}
