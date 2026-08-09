package com.aether.agent.skill.vo;

import lombok.Data;

/** AI-generated resource draft; the client must explicitly save it as a Skill resource. */
@Data
public class AgentSkillResourceGenerateVo {
    private String name;
    private String type;
    private String purpose;
    private String content;
    private String model;
}
