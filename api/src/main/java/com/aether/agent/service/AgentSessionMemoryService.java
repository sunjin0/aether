package com.aether.agent.service;

import com.aether.agent.entity.AgentSessionMemory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 定义智能体会话Memory业务服务契约。
 */
public interface AgentSessionMemoryService extends IService<AgentSessionMemory> {
    /**
     * 处理record任务Conclusion。
     */
    void recordTaskConclusion(String sessionId, String taskId, String runId, String content);

    /**
     * 查询Injectable。
     */
    List<AgentSessionMemory> listInjectable(String sessionId, int limit);

    /**
     * 处理expireDueMemories。
     */
    int expireDueMemories();
}
