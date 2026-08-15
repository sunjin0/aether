package com.aether.agent.service;

import com.aether.agent.entity.AgentSession;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentSessionService extends IService<AgentSession> {
    AgentSession getOrCreate(String conversationId, String userId, String agentDefinitionId);
    void touch(String sessionId);
    boolean claimTask(String sessionId, String taskId);
    void updateTaskState(String sessionId, String taskId, String taskStatus);
}
