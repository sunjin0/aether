package com.aether.agent.skill.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Skill 列表查询和展示对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSkillVo extends BaseEntity {
    private String name;
    private String code;
    private String description;
    private String category;
    private Integer status;
    private String currentVersionId;
    private String icon;
    private String tags;
    private Long current = 1L;
    private Long pageSize = 10L;
}
