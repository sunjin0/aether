package com.aether.agent.skill.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill 列表查询和展示对象。
 */
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
    /**
     * 列表生命周期摘要，避免管理员逐条进入详情确认配置状态。
     */
    private Boolean hasDraft;
    private Integer currentVersionNo;
    private Long installedAgentCount;
    private Long toolCount;
    private Long knowledgeBaseCount;
    private Long resourceCount;
    private Long current = 1L;
    private Long pageSize = 10L;
}
