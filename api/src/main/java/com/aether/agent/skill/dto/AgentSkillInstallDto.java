package com.aether.agent.skill.dto;

import lombok.Data;

/**
 * 将已发布 Skill 版本安装到 Agent 的请求参数。
 */
@Data
public class AgentSkillInstallDto {
    private String skillVersionId;
    private Integer priority;
    private Integer status;
    private String configOverrides;
}
