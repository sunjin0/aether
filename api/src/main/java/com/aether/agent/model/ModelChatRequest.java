package com.aether.agent.model;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import lombok.Data;

import java.util.List;

/**
 * 模型聊天请求。
 */
@Data
public class ModelChatRequest {

    private ModelProvider provider;

    private AgentDefinition agent;

    private List<ModelChatMessage> messages;

    private List<AgentTool> tools;
}
