package com.aether.agent.skill.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Safe, user-facing metadata for a generated artifact. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentArtifactVo extends BaseEntity {
    private String runId;
    private String messageId;
    private String agentDefinitionId;
    private String agentDefinitionName;
    private String fileName;
    private String contentType;
    private Long size;
    private Long expiresAt;
    private Long recycledAt;
    private Long recycleExpiresAt;
    private Integer status;
}
