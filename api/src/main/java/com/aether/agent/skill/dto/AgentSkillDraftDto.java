package com.aether.agent.skill.dto;

import lombok.Data;

import java.util.List;

/** 创建或编辑 Skill 草稿的请求参数。 */
@Data
public class AgentSkillDraftDto {
    private String name;
    private String code;
    private String description;
    private String category;
    private String icon;
    private String tags;
    private String instruction;
    private String inputSchema;
    private String outputSchema;
    private String toolPolicy;
    private String changeNote;
    private List<AgentSkillToolDto> tools;
    private List<String> knowledgeBaseIds;
}
