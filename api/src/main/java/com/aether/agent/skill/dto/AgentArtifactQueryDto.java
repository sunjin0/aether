package com.aether.agent.skill.dto;

import lombok.Data;

/**
 * Query parameters for the current user's generated-file library.
 */
@Data
public class AgentArtifactQueryDto {
    private Long current = 1L;
    private Long pageSize = 24L;
    private String fileName;
    private String extension;
    private String agentDefinitionId;
    private Long startTime;
    private Long endTime;
    /**
     * false: active library; true: recycle bin.
     */
    private Boolean recycled = false;
}
