package com.aether.agent.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("agent_run_plan_step") public class AgentRunPlanStep extends BaseEntity { private String planVersionId, stepKey, title, status, resultSummary, idempotencyKey; private Integer sequence, attemptCount; private Long startedAt, completedAt; }
