package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_session")
public class AgentSession extends BaseEntity {
    private String conversationId;
    private String agentDefinitionId;
    private String userId;
    /** LangGraph 的稳定线程标识，始终与 Session 生命周期一致。 */
    private String graphThreadId;
    private String status;
    private String activeTaskId;
    private Integer memoryVersion;
    private Long lastActiveAt;
}
