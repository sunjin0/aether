package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run_step")
public class AgentRunStep extends BaseEntity {
    private String runId;
    private String eventId;
    private String eventType;
    private Long occurredAt;
    private String data;
}
