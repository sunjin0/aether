package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;

public interface AdminPreferenceExtractionService {

    void extractAsync(String userId,
                      String conversationId,
                      AgentMessage userMessage,
                      AgentMessage assistantMessage,
                      AgentDefinition agent,
                      ModelProvider provider);
}
