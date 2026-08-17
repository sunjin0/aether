package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;

import java.util.List;

/**
 * 定义会话Memory业务服务契约。
 */
public interface ConversationMemoryService {

    /**
     * 处理storeMemory。
     */
    void storeMemory(String conversationId, String userId, String content);

    /**
     * 处理retrieveRelevantMemories。
     */
    List<String> retrieveRelevantMemories(String userId, String query, int topK);

    /**
     * 删除会话Memories。
     */
    void deleteConversationMemories(String conversationId);

    /**
     * 处理storeSegment。
     */
    void storeSegment(String conversationId, String userId, List<String> messages, AgentDefinition agent, ModelProvider provider);
}
