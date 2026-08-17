package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体技能主记录，保存稳定身份和当前发布版本指针。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill")
public class AgentSkill extends BaseEntity {
    private String name;
    private String code;
    private String description;
    private String category;
    private Integer status;
    private String currentVersionId;
    private String icon;
    private String tags;
}
