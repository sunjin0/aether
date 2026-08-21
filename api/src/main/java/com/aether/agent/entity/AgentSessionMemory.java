package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体会话Memory。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_session_memory")
public class AgentSessionMemory extends BaseEntity {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_DELETED = "DELETED";

    private String sessionId;
    private String memoryType;
    private String content;
    private String summary;
    private String sourceMessageId;
    private String sourceEventRange;
    private String sourceTaskId;
    private String sourceRunId;
    private String extractorVersion;
    private String candidateHash;
    private Integer importance;
    private Integer confidence;
    private String status;
    private String sensitivityLevel;
    private String supersededById;
    private String correctionReason;
    private Long expiresAt;
    private Integer memoryVersion;
}
