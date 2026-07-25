package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;

import java.util.List;

public interface ConversationMemoryService {

    void storeMemory(String conversationId, String userId, String content);

    List<String> retrieveRelevantMemories(String userId, String query, int topK);

    void deleteConversationMemories(String conversationId);

    void storeSegment(String conversationId, String userId, List<String> messages, AgentDefinition agent, ModelProvider provider);
}
