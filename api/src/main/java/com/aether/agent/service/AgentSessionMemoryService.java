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
     * 记录经校验的自动提取记忆。
     */
    AgentSessionMemory recordExtractedMemory(String sessionId, String memoryType, String content,
                                             String sourceMessageId, Integer confidence,
                                             String sensitivityLevel);

    /**
     * 记录经校验的自动提取记忆，并保存提取来源元数据。
     */
    AgentSessionMemory recordExtractedMemory(String sessionId, String memoryType, String content,
                                             String sourceMessageId, Integer confidence,
                                             String sensitivityLevel, String extractorVersion,
                                             String candidateHash, String sourceEventRange);

    /**
     * 查询Injectable。
     */
    List<AgentSessionMemory> listInjectable(String sessionId, int limit);

    /**
     * 以治理规则过滤的活跃记忆：仅 {@code ACTIVE} 且未过期、非敏感受限的记忆进入模型输入。
     */
    List<AgentSessionMemory> listInjectableForModel(String sessionId, int limit);

    /**
     * 用户修正记忆：创建新 ACTIVE 记录，并将旧记录标记为 SUPERSEDED。
     */
    AgentSessionMemory correctMemory(String sessionId, String memoryId, String content,
                                     String reason, Integer expectedMemoryVersion);

    /**
     * 用户删除记忆：从未来上下文移除。
     */
    void deleteMemory(String sessionId, String memoryId, Integer expectedMemoryVersion, String reason);

    /**
     * 用户反馈记忆状态。
     */
    AgentSessionMemory feedback(String sessionId, String memoryId, Integer expectedMemoryVersion,
                                String verdict, String reason);

    /**
     * 处理expireDueMemories。
     */
    int expireDueMemories();
}
