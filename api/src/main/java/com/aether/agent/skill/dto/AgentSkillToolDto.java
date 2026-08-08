package com.aether.agent.skill.dto;

import lombok.Data;

/** 草稿中声明的工具依赖及其必需性。 */
@Data
public class AgentSkillToolDto {
    private String toolId;
    private Boolean required;
    private Integer priority;
}
