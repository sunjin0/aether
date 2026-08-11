package com.aether.agent.skill.dto;

import lombok.Data;

/** Request for an AI-generated, not-yet-persisted Skill resource draft. */
@Data
public class AgentSkillResourceGenerateDto {
    private String modelId;
    private String type;
    private String name;
    private String purpose;
    private String prompt;
}
