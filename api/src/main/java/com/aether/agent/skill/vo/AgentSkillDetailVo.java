package com.aether.agent.skill.vo;

import com.aether.agent.skill.entity.*;
import lombok.Data;

import java.util.List;

/**
 * Skill 详情，包含当前草稿或发布版本的依赖和资源。
 */
@Data
public class AgentSkillDetailVo {
    private AgentSkill skill;
    private AgentSkillVersion draft;
    private AgentSkillVersion currentVersion;
    private List<AgentSkillToolBinding> tools;
    private List<AgentSkillKnowledgeBinding> knowledgeBases;
    private List<AgentSkillResource> resources;
}
