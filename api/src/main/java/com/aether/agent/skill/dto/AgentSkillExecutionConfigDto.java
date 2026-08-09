package com.aether.agent.skill.dto;

import lombok.Data;
import java.util.List;

/** Editable execution declaration for a Skill draft. */
@Data
public class AgentSkillExecutionConfigDto {
    private Boolean enabled;
    private String entryResourceId;
    private String runtime;
    private List<String> outputFormats;
    private Integer timeoutSeconds;
    private Integer maxOutputFiles;
    private Long maxOutputBytes;
}
