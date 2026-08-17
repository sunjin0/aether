package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 安装的技能版本，固定到明确发布版本且不自动跟随最新版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_definition_skill_binding")
public class AgentDefinitionSkillBinding extends BaseEntity {
    private String agentDefinitionId;
    private String skillId;
    private String skillVersionId;
    private Integer priority;
    private Integer status;
    private String configOverrides;
}
