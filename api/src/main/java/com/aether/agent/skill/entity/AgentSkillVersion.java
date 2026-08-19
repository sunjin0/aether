package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能版本快照；草稿可编辑，发布后只能读取或逻辑停用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_version")
public class AgentSkillVersion extends BaseEntity {
    private String skillId;
    private Integer versionNo;
    private String instruction;
    private String inputSchema;
    private String outputSchema;
    private String toolPolicy;
    private String routingSummary;
    private String triggerTerms;
    private String excludeTerms;
    private String routingKeywords;
    private String routingExamples;
    private Integer status;
    private String changeNote;
    private Long publishedAt;
    private String publishedBy;
}
