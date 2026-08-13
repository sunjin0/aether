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
    /** Owner copied from the sandbox execution to support secure file-library queries. */
    private String userId;
    /** Agent copied from the sandbox execution for file-library source filtering. */
    private String agentDefinitionId;
    private String fileName;
    private String objectKey;
    private String contentSha256;
    /** Unique idempotency key used only by new sandbox callback uploads. */
    private String callbackKey;
    private String contentType;
    private Long size;
    private Long expiresAt;
    /** Timestamp when the owner moved the artifact to the recycle bin. */
    private Long recycledAt;
    private String logSummary;
    private Integer status;
}
