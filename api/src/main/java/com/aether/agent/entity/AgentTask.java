package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTask extends BaseEntity {
    private String sessionId;
    private String userId;
    private String agentDefinitionId;
    private String title;
    private String status;
    private String currentRunId;
    private String pauseReason;
}
