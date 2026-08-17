package com.aether.agent.service;

import com.aether.agent.entity.AgentTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义智能体任务业务服务契约。
 */
public interface AgentTaskService extends IService<AgentTask> {
    /**
     * 创建当前请求。
     */
    AgentTask create(String sessionId, String userId, String agentDefinitionId, String title);

    /**
     * 更新状态。
     */
    void updateStatus(String taskId, String status, String runId, String pauseReason);

    /**
     * 下一个Queued。
     */
    AgentTask nextQueued(String sessionId);

    /**
     * 查找Active。
     */
    AgentTask findActive(String sessionId);
}
