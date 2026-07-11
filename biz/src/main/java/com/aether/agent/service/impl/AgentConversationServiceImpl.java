package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.mapper.AgentConversationMapper;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.vo.AgentConversationLifecycleVo;
import com.aether.agent.vo.AgentMessageStatisticsVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 会话 Service 实现
 */
@Service
public class AgentConversationServiceImpl extends ServiceImpl<AgentConversationMapper, AgentConversation> implements AgentConversationService {

    private final AgentMessageService agentMessageService;

    @Autowired
    public AgentConversationServiceImpl(AgentMessageService agentMessageService) {
        this.agentMessageService = agentMessageService;
    }

    @Override
    public AgentConversationLifecycleVo getLifecycle(String conversationId) {
        AgentConversation conversation = getById(conversationId);
        if (conversation == null) {
            return null;
        }

        // 获取最后一条消息时间
        AgentMessage lastMessage = agentMessageService.getOne(
                Wrappers.lambdaQuery(AgentMessage.class)
                        .eq(AgentMessage::getConversationId, conversationId)
                        .eq(AgentMessage::getDeleted, false)
                        .orderByDesc(AgentMessage::getCreatedAt)
                        .last("LIMIT 1")
        );

        Long lastActiveAt = lastMessage != null ? lastMessage.getCreatedAt() : conversation.getCreatedAt();

        // 统计用户消息和助手消息数量
        Long totalUserMessages = agentMessageService.count(
                Wrappers.lambdaQuery(AgentMessage.class)
                        .eq(AgentMessage::getConversationId, conversationId)
                        .eq(AgentMessage::getRole, "user")
                        .eq(AgentMessage::getDeleted, false)
        );

        Long totalAssistantMessages = agentMessageService.count(
                Wrappers.lambdaQuery(AgentMessage.class)
                        .eq(AgentMessage::getConversationId, conversationId)
                        .eq(AgentMessage::getRole, "assistant")
                        .eq(AgentMessage::getDeleted, false)
        );

        AgentConversationLifecycleVo vo = new AgentConversationLifecycleVo();
        vo.setConversationId(conversationId);
        vo.setCreatedAt(conversation.getCreatedAt());
        vo.setLastActiveAt(lastActiveAt);
        vo.setClosedAt(conversation.getStatus() == 1 ? conversation.getUpdatedAt() : null);
        vo.setStatus(conversation.getStatus());
        vo.setMessageCount(conversation.getMessageCount());
        vo.setTotalUserMessages(totalUserMessages);
        vo.setTotalAssistantMessages(totalAssistantMessages);
        vo.setDurationMs(lastActiveAt != null && conversation.getCreatedAt() != null ?
                lastActiveAt - conversation.getCreatedAt() : 0L);

        return vo;
    }

    @Override
    public AgentMessageStatisticsVo getStatistics(String conversationId) {
        AgentConversation conversation = getById(conversationId);
        if (conversation == null) {
            return null;
        }

        // 获取所有未删除的消息
        java.util.List<AgentMessage> messages = agentMessageService.list(
                Wrappers.lambdaQuery(AgentMessage.class)
                        .eq(AgentMessage::getConversationId, conversationId)
                        .eq(AgentMessage::getDeleted, false)
        );

        long totalMessages = messages.size();
        long userMessages = 0;
        long assistantMessages = 0;
        long toolMessages = 0;
        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;
        long latencySamples = 0;

        for (AgentMessage msg : messages) {
            String role = msg.getRole();
            if ("user".equals(role)) {
                userMessages++;
            } else if ("assistant".equals(role)) {
                assistantMessages++;
                totalPromptTokens += safeLong(msg.getPromptTokens());
                totalCompletionTokens += safeLong(msg.getCompletionTokens());
                totalTokens += safeLong(msg.getTotalTokens());
                if (msg.getLatencyMs() != null) {
                    totalLatencyMs += msg.getLatencyMs();
                    latencySamples++;
                }
            } else if ("tool".equals(role)) {
                toolMessages++;
            }
        }

        AgentMessageStatisticsVo vo = new AgentMessageStatisticsVo();
        vo.setConversationId(conversationId);
        vo.setTotalMessages(totalMessages);
        vo.setUserMessages(userMessages);
        vo.setAssistantMessages(assistantMessages);
        vo.setToolMessages(toolMessages);
        vo.setTotalPromptTokens(totalPromptTokens);
        vo.setTotalCompletionTokens(totalCompletionTokens);
        vo.setTotalTokens(totalTokens);
        vo.setAvgLatencyMs(latencySamples == 0L ? 0L : totalLatencyMs / latencySamples);

        return vo;
    }

    private long safeLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
