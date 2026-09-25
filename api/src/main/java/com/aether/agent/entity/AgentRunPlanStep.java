package com.aether.agent.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体运行PlanStep。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run_plan_step")
public class AgentRunPlanStep extends AccountOwnedEntity {
    private String planVersionId, stepKey, title, status, resultSummary, idempotencyKey;
    private Integer sequence, attemptCount;
    private Long startedAt, completedAt;
}
