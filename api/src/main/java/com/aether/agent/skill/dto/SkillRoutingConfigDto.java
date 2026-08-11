package com.aether.agent.skill.dto;

import lombok.Data;

/** Global discovery configuration. It does not change frozen Skill versions. */
@Data
public class SkillRoutingConfigDto {
    private String embeddingModelId;
}
