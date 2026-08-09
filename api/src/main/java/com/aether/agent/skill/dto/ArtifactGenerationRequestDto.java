package com.aether.agent.skill.dto;

import lombok.Data;
import java.util.Map;

/** The only artifact request accepted from MCP; it deliberately has no command or infrastructure fields. */
@Data
public class ArtifactGenerationRequestDto {
    private String skillCode;
    private Map<String, Object> input;
}
