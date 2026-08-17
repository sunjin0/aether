package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能版本声明的知识库范围，用于收窄 Agent 检索范围。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_knowledge_binding")
public class AgentSkillKnowledgeBinding extends BaseEntity {
    private String skillVersionId;
    private String knowledgeBaseId;
}
