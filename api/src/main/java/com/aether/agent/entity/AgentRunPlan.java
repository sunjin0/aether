package com.aether.agent.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体运行Plan。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run_plan")
public class AgentRunPlan extends AccountOwnedEntity {
    private String runId, taskId, currentStepId, status, pauseReason;
    private Integer currentVersion;
    private Long lastActiveAt;
}
