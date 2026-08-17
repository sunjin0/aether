package com.aether.agent.skill.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentDefinitionSkillBindingVo extends BaseEntity {
    private String skillId;
    private String skillVersionId;
    private Integer priority;
    private Integer status;
    private String skillName;
    private String skillCode;
    private String skillDescription;
    private String category;
    private Integer versionNo;
    private String keyword;
    private Long current;
    private Long pageSize;
}
