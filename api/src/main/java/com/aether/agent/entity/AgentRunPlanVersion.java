package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体运行PlanVersion。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run_plan_version")
public class AgentRunPlanVersion extends BaseEntity {
    private String planId, reason, summary, snapshot;
    private Integer version;
}
