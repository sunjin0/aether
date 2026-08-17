package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能版本声明的 MCP 工具依赖，用于收窄 Agent 工具范围。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_tool_binding")
public class AgentSkillToolBinding extends BaseEntity {
    private String skillVersionId;
    private String toolId;
    private Boolean required;
    private Integer priority;
}
