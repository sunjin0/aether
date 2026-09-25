package com.aether.agent.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体运行Step。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run_step")
public class AgentRunStep extends AccountOwnedEntity {
    private String runId;
    private String eventId;
    private String eventType;
    private Long occurredAt;
    private String data;
}
