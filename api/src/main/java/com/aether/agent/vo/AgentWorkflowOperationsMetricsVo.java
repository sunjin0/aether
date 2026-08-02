package com.aether.agent.vo;

import lombok.Data;

/** 工作流运营观测聚合指标。 */
@Data
public class AgentWorkflowOperationsMetricsVo {
    private Long totalInstances;
    private Long completedInstances;
    private Long failedInstances;
    private Long waitingUserInstances;
    private Double completionRate;
    private Double averageCompletedDurationMs;
    private Double averageNodeDurationMs;
    private Double averageWaitingUserDurationMs;
    private Long callbackFailedCount;
    private Long mcpFailedCount;
    private Long executionDeadLetterCount;
}
