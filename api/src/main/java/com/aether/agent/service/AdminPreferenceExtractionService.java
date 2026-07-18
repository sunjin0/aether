package com.aether.agent.service;


import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;

public interface AdminPreferenceExtractionService {
    /**
     * 用户偏好提取
     *
     * @param userId          用户ID
     * @param conversationId  会话ID
     * @param userMessage     用户消息
     * @param assistantMessage  机器人消息
     * @param agent           代理定义
     * @param provider        模型提供者
     */
    void extractAsync(String userId, String conversationId,
                      AgentMessage userMessage, AgentMessage assistantMessage,
                      AgentDefinition agent, ModelProvider provider);
}
