package com.aether.agent.service;

import com.aether.agent.entity.AgentSession;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义智能体会话业务服务契约。
 */
public interface AgentSessionService extends IService<AgentSession> {
    /**
     * 获取Or创建。
     */
    AgentSession getOrCreate(String conversationId, String userId, String agentDefinitionId);

    /**
     * 处理touch。
     */
    void touch(String sessionId);

    /**
     * 处理claim任务。
     */
    boolean claimTask(String sessionId, String taskId);

    /**
     * 更新任务State。
     */
    void updateTaskState(String sessionId, String taskId, String taskStatus);
}
