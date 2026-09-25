package com.aether.agent.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTask extends AccountOwnedEntity {
    private String sessionId;
    private String userId;
    private String agentDefinitionId;
    private String title;
    private String status;
    private String currentRunId;
    private String pauseReason;
}
