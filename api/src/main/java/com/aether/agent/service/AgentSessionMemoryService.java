package com.aether.agent.service;

import com.aether.agent.entity.AgentSessionMemory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentSessionMemoryService extends IService<AgentSessionMemory> {
    void recordTaskConclusion(String sessionId, String taskId, String runId, String content);

    List<AgentSessionMemory> listInjectable(String sessionId, int limit);

    int expireDueMemories();
}
