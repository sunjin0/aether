package com.aether.agent.service;
import com.aether.agent.entity.AgentTask;
import com.baomidou.mybatisplus.extension.service.IService;
public interface AgentTaskService extends IService<AgentTask> {
    AgentTask create(String sessionId, String userId, String agentDefinitionId, String title);
    void updateStatus(String taskId, String status, String runId, String pauseReason);
    AgentTask nextQueued(String sessionId);
    AgentTask findActive(String sessionId);
}
