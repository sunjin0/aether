package com.aether.agent.service.impl;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Agent聊天服务实现。
 */
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private static final int AGENT_STATUS_ENABLED = 1;
    private static final int PROVIDER_STATUS_ENABLED = 1;
    private static final int CONVERSATION_STATUS_OPEN = 0;
    private static final int RUN_STATUS_SUCCESS = 0;
    private static final int RUN_STATUS_FAILED = 1;

    private final AgentDefinitionService agentDefinitionService;
    private final ModelProviderService modelProviderService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final AgentRunService agentRunService;
    private final ModelClientFactory modelClientFactory;

    public AgentChatServiceImpl(AgentDefinitionService agentDefinitionService,
                                ModelProviderService modelProviderService,
                                AgentConversationService agentConversationService,
                                AgentMessageService agentMessageService,
                                AgentRunService agentRunService,
                                ModelClientFactory modelClientFactory) {
        this.agentDefinitionService = agentDefinitionService;
        this.modelProviderService = modelProviderService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.agentRunService = agentRunService;
        this.modelClientFactory = modelClientFactory;
    }

    @Override
    public AgentMessageVo chat(AgentChatDto dto) {
        validateRequest(dto);
        String userId = getCurrentUserId();
        long startTime = System.currentTimeMillis();

        AgentDefinition agent = getEnabledAgent(dto.getAgentId());
        ModelProvider provider = getEnabledProvider(agent.getModelProviderId());
        AgentConversation conversation = getOrCreateConversation(dto, userId, agent);
        AgentMessage userMessage = saveUserMessage(conversation.getId(), dto.getMessage());

        try {
            List<ModelChatMessage> context = buildContext(agent, conversation.getId());
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(context);

            ModelClient modelClient = modelClientFactory.getClient(provider);
            ModelChatResponse modelResponse = modelClient.chat(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            AgentMessage assistantMessage = saveAssistantMessage(conversation.getId(), modelResponse, latencyMs);
            updateConversationMessageCount(conversation.getId());
            saveRun(agent, provider, userId, conversation.getId(), assistantMessage.getId(), dto.getMessage(), modelResponse, latencyMs, RUN_STATUS_SUCCESS, null);

            AgentMessageVo vo = new AgentMessageVo();
            BeanUtils.copyProperties(assistantMessage, vo);
            return vo;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            saveFailedRun(agent, provider, userId, conversation.getId(), userMessage.getId(), dto.getMessage(), latencyMs, e);
            throw e;
        }
    }

    private void validateRequest(AgentChatDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getAgentId()) || StringUtils.isBlank(dto.getMessage())) {
            throw new ServerException(400, "参数错误");
        }
    }

    private String getCurrentUserId() {
        HashMap<String, String> currentUser = CurrentUser.getUser();
        String userId = currentUser == null ? null : currentUser.get("userId");
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
            conversation.setStatus(CONVERSATION_STATUS_OPEN);
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
        message.setContent(content);
        agentMessageService.save(message);
        return message;
    }

    private List<ModelChatMessage> buildContext(AgentDefinition agent, String conversationId) {
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
        message.setContent(modelResponse.getContent());
        message.setModel(modelResponse.getModel());
        message.setPromptTokens(modelResponse.getPromptTokens());
        message.setCompletionTokens(modelResponse.getCompletionTokens());
        message.setTotalTokens(modelResponse.getTotalTokens());
        message.setLatencyMs((int) latencyMs);
        agentMessageService.save(message);
        return message;
    }

    private void updateConversationMessageCount(String conversationId) {
        long count = agentMessageService.count(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getDeleted, false));
        AgentConversation conversation = new AgentConversation();
        conversation.setId(conversationId);
        conversation.setMessageCount((int) count);
        agentConversationService.updateById(conversation);
    }

    private void saveFailedRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                               String messageId, String input, long latencyMs, RuntimeException e) {
        ModelChatResponse response = new ModelChatResponse();
        response.setModel(agent.getModel());
        saveRun(agent, provider, userId, conversationId, messageId, input, response, latencyMs, RUN_STATUS_FAILED, e.getMessage());
    }

    private void saveRun(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
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
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
