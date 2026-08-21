package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;

/**
 * 定义会话记忆自动提取服务。
 */
public interface AgentSessionMemoryExtractionService {
    /**
     * 在一轮消息完成后异步提取可进入会话记忆的候选项。
     */
    void extractAsync(String userId, String conversationId,
                      AgentMessage userMessage, AgentMessage assistantMessage,
                      AgentDefinition agent, ModelProvider provider);
}
