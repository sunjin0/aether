package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A validated downloadable output produced by a sandbox execution. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_artifact")
public class AgentArtifact extends BaseEntity {
    private String executionId;
    private String runId;
    private String skillVersionId;
    private String messageId;
    private String fileName;
    private String objectKey;
    private String contentSha256;
    private String contentType;
    private Long size;
    private Long expiresAt;
    private String logSummary;
    private Integer status;
}
