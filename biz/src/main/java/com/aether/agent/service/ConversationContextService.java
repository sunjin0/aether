package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 组装模型调用所需的会话上下文。
 * 缓存交由 {@link ConversationCacheService}，摘要交由 {@link ConversationSummaryService}。
 */
@Service
public class ConversationContextService {
    private static final int HISTORY_MESSAGE_LIMIT = 20;
    private static final int SUMMARY_TRIGGER_THRESHOLD = 10;
    private static final int KEEP_RECENT_MESSAGES = 5;

    private final AgentMessageService messageService;
    private final ConversationCacheService cacheService;
    private final ConversationSummaryService summaryService;

    public ConversationContextService(AgentMessageService messageService,
                                      ConversationCacheService cacheService,
                                      ConversationSummaryService summaryService) {
        this.messageService = messageService;
        this.cacheService = cacheService;
        this.summaryService = summaryService;
    }

    /** 查询缓存，缓存未命中时从数据库读取完整的近期上下文。 */
    public List<ModelChatMessage> getOrBuildRecent(AgentDefinition agent, String conversationId) {
        List<ModelChatMessage> cached = cacheService.get(conversationId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<ModelChatMessage> context = buildFromHistory(agent, conversationId);
        cacheService.put(conversationId, context);
        return context;
    }

    /** 追加已持久化的用户、助手或工具交互消息。 */
    public void append(String conversationId, ModelChatMessage message) {
        cacheService.append(conversationId, message);
    }

    /** 从持久化消息构建系统提示与最近 20 条用户/助手消息。 */
    public List<ModelChatMessage> buildFromHistory(AgentDefinition agent, String conversationId) {
        List<ModelChatMessage> context = createSystemContext(agent);
        List<AgentMessage> messages = queryMessages(conversationId, HISTORY_MESSAGE_LIMIT, true);
        Collections.reverse(messages);
        addMessages(context, messages);
        return context;
    }

    /** 构建摘要与最近消息混合的模型上下文。 */
    public List<ModelChatMessage> buildWithSummary(AgentDefinition agent, ModelProvider provider, String conversationId) {
        List<ModelChatMessage> context = createSystemContext(agent);
        int fetchLimit = SUMMARY_TRIGGER_THRESHOLD + KEEP_RECENT_MESSAGES + 1;
        List<AgentMessage> messages = queryMessages(conversationId, fetchLimit, false);
        if (messages.size() <= SUMMARY_TRIGGER_THRESHOLD) {
            addMessages(context, messages);
            return context;
        }

        int splitIndex = messages.size() - KEEP_RECENT_MESSAGES;
        String summary = summaryService.getOrCreate(conversationId,
                new ArrayList<AgentMessage>(messages.subList(0, splitIndex)), agent, provider);
        if (StringUtils.isNotBlank(summary)) {
            context.add(new ModelChatMessage("system", "【对话历史摘要】" + summary));
        }
        addMessages(context, messages.subList(splitIndex, messages.size()));
        return context;
    }

    private List<ModelChatMessage> createSystemContext(AgentDefinition agent) {
        List<ModelChatMessage> context = new ArrayList<ModelChatMessage>();
        if (agent != null && StringUtils.isNotBlank(agent.getSystemPrompt())) {
            context.add(new ModelChatMessage("system", agent.getSystemPrompt()));
        }
        return context;
    }

    private List<AgentMessage> queryMessages(String conversationId, int limit, boolean descending) {
        return messageService.list(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant")
                .orderBy(true, !descending, AgentMessage::getCreatedAt)
                .last("limit " + limit));
    }

    private void addMessages(List<ModelChatMessage> context, List<AgentMessage> messages) {
        for (AgentMessage message : messages) {
            context.add(new ModelChatMessage(message.getRole(), message.getContent()));
        }
    }
}
