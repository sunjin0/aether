package com.aether.agent.service;

import com.aether.agent.vo.AgentRunPlanVo;

/**
 * 定义智能体运行Plan业务服务契约。
 */
public interface AgentRunPlanService {
    /**
     * 处理recordPlan。
     */
    void recordPlan(String runId, String reason, String summary, String snapshot);

    /**
     * 处理recordPlan。
     */
    void recordPlan(String runId, String taskId, String reason, String summary, String snapshot);

    /**
     * 处理markPaused。
     */
    void markPaused(String runId, String reason);

    /**
     * 处理markRunning。
     */
    void markRunning(String runId);

    /**
     * 处理markStepRunning。
     */
    void markStepRunning(String runId, Integer stepIndex);

    /**
     * 处理markStepVerified。
     */
    void markStepVerified(String runId, Integer stepIndex, String verification);

    /**
     * 详情当前请求。
     */
    AgentRunPlanVo detail(String runId);

    /**
     * 详情按任务Id。
     */
    AgentRunPlanVo detailByTaskId(String taskId);
}
