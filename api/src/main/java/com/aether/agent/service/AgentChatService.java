package com.aether.agent.service;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.vo.AgentMessageVo;

/**
 * Agent聊天服务。
 */
public interface AgentChatService {

    AgentMessageVo chat(AgentChatDto dto);

    void stream(AgentChatDto dto, AgentStreamCallback callback);

    AgentDefinition getEnabledAgent(String agentId);
}
