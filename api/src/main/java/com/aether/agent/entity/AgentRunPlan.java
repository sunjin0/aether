package com.aether.agent.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("agent_run_plan") public class AgentRunPlan extends BaseEntity { private String runId, taskId, currentStepId, status, pauseReason; private Integer currentVersion; private Long lastActiveAt; }
