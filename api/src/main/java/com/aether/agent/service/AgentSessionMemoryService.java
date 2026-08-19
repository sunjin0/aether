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
     * 以治理规则过滤的活跃记忆：仅 {@code ACTIVE} 且未过期、非敏感受限的记忆进入模型输入。
     */
    List<AgentSessionMemory> listInjectableForModel(String sessionId, int limit);

    /**
     * 处理expireDueMemories。
     */
    int expireDueMemories();
}
