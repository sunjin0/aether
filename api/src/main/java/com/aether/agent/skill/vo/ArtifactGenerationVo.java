package com.aether.agent.skill.vo;

import lombok.Data;

/** MCP-visible acknowledgement; the artifact is later attached to the conversation by the platform. */
@Data
public class ArtifactGenerationVo {
    private String executionId;
    private String runId;
    private String status;
}
