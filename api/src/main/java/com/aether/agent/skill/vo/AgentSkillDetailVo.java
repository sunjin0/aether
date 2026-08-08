package com.aether.agent.skill.vo;

import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillKnowledgeBinding;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillToolBinding;
import com.aether.agent.skill.entity.AgentSkillVersion;
import lombok.Data;

import java.util.List;

/** Skill 详情，包含当前草稿或发布版本的依赖和资源。 */
@Data
public class AgentSkillDetailVo {
    private AgentSkill skill;
    private AgentSkillVersion draft;
    private AgentSkillVersion currentVersion;
    private List<AgentSkillToolBinding> tools;
    private List<AgentSkillKnowledgeBinding> knowledgeBases;
    private List<AgentSkillResource> resources;
}
