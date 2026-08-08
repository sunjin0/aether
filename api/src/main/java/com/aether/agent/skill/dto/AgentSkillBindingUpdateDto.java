package com.aether.agent.skill.dto;

import lombok.Data;

/** 更新 Agent 已安装 Skill 的版本、优先级或状态。 */
@Data
public class AgentSkillBindingUpdateDto {
    private String skillVersionId;
    private Integer priority;
    private Integer status;
    private String configOverrides;
}
