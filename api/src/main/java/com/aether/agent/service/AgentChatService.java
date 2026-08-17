package com.aether.agent.service;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.vo.AgentMessageVo;

/**
 * Agent聊天服务。
 */
public interface AgentChatService {

    /**
     * 对话当前请求。
     */
    AgentMessageVo chat(AgentChatDto dto);

    /**
     * 处理stream。
     */
    void stream(AgentChatDto dto, AgentStreamCallback callback);

    /**
     * 获取Enabled智能体。
     */
    AgentDefinition getEnabledAgent(String agentId);
}
