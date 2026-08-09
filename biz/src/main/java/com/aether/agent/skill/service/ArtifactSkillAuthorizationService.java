package com.aether.agent.skill.service;

import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillExecutionConfig;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillExecutionConfigServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves the artifact-capable Skill codes frozen for an Agent tool invocation. */
@Service
public class ArtifactSkillAuthorizationService {
    private final AgentSkillService skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillExecutionConfigServiceImpl configService;

    public ArtifactSkillAuthorizationService(AgentSkillService skillService,
                                             AgentSkillVersionServiceImpl versionService,
                                             AgentSkillExecutionConfigServiceImpl configService) {
        this.skillService = skillService;
        this.versionService = versionService;
        this.configService = configService;
    }

    public Set<String> resolve(String agentId) {
        Set<String> result = new LinkedHashSet<String>();
        for (AgentDefinitionSkillBinding binding : skillService.listBindings(agentId)) {
            if (!Integer.valueOf(1).equals(binding.getStatus())) continue;
            AgentSkill skill = skillService.getById(binding.getSkillId());
            AgentSkillVersion version = versionService.getById(binding.getSkillVersionId());
            if (skill == null || version == null || !Integer.valueOf(1).equals(skill.getStatus())
                    || !Integer.valueOf(1).equals(version.getStatus())) continue;
            AgentSkillExecutionConfig config = configService.getOne(Wrappers.lambdaQuery(AgentSkillExecutionConfig.class)
                    .eq(AgentSkillExecutionConfig::getSkillVersionId, version.getId()));
            if (config != null && Boolean.TRUE.equals(config.getEnabled())) result.add(skill.getCode());
        }
        return result;
    }
}
