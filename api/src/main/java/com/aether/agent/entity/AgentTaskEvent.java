package com.aether.agent.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体任务事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task_event")
public class AgentTaskEvent extends AccountOwnedEntity {
    private String taskId, runId, eventType, summary, data;
    private Long occurredAt;
}
