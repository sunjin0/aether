package com.aether.agent.skill.service;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.skill.entity.*;
import com.aether.agent.skill.service.AgentSkillService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 解析 Agent 已安装的 Skill 并计算最终最小授权集合。
 * 该服务不允许客户端传入 Skill ID，输入只能按已安装 Skill code 提供。
 */
@Service
public class SkillContextService {
    private final AgentSkillService skillService;
    private final com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl versionService;
    private final com.aether.agent.skill.service.impl.AgentSkillToolBindingServiceImpl toolBindingService;
    private final com.aether.agent.skill.service.impl.AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService;
    private final com.aether.agent.tools.AgentToolCatalog toolCatalog;

    public SkillContextService(AgentSkillService skillService,
                               com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl versionService,
                               com.aether.agent.skill.service.impl.AgentSkillToolBindingServiceImpl toolBindingService,
                               com.aether.agent.skill.service.impl.AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService,
                               com.aether.agent.tools.AgentToolCatalog toolCatalog) {
        this.skillService = skillService;
        this.versionService = versionService;
        this.toolBindingService = toolBindingService;
        this.knowledgeBindingService = knowledgeBindingService;
        this.toolCatalog = toolCatalog;
    }

    public SkillRuntimeContext resolve(AgentDefinition agent, AgentChatDto dto) {
        List<AgentDefinitionSkillBinding> installations = skillService.listBindings(agent.getId()).stream()
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .sorted((left, right) -> Integer.compare(left.getPriority() == null ? 0 : left.getPriority(), right.getPriority() == null ? 0 : right.getPriority()))
                .collect(Collectors.toList());
        SkillRuntimeContext context = new SkillRuntimeContext();
        if (installations.isEmpty()) {
            context.setSystemPrompt(StringUtils.defaultString(agent.getSystemPrompt()));
            context.setTools(toolCatalog.getBoundTools(agent.getId()));
            context.setKnowledgeBaseIds(null);
            context.setSnapshot("{\"installed\":false}");
            return context;
        }
        if (installations.size() > 3) throw new IllegalArgumentException("Agent has more than three enabled Skills");

        Map<String, Map<String, Object>> inputs = dto == null || dto.getSkillInputs() == null ? Collections.emptyMap() : dto.getSkillInputs();
        Set<String> allowedToolIds = null;
        Set<String> allowedKnowledgeBaseIds = null;
        StringBuilder prompt = new StringBuilder(StringUtils.defaultString(agent.getSystemPrompt()));
        List<Map<String, Object>> snapshotSkills = new ArrayList<>();
        Set<String> installedCodes = new LinkedHashSet<>();
        for (AgentDefinitionSkillBinding installation : installations) {
            AgentSkill skill = skillService.getById(installation.getSkillId());
            AgentSkillVersion version = versionService.getById(installation.getSkillVersionId());
            if (skill == null || version == null || !Integer.valueOf(1).equals(skill.getStatus()) || !Integer.valueOf(1).equals(version.getStatus())) {
                throw new IllegalArgumentException("Installed Skill version is unavailable");
            }
            installedCodes.add(skill.getCode());
            List<AgentSkillToolBinding> declarations = toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, version.getId()));
            Set<String> toolIds = declarations.stream().map(AgentSkillToolBinding::getToolId).collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> liveToolIds = toolCatalog.getBoundTools(agent.getId()).stream().map(AgentTool::getId).collect(Collectors.toSet());
            for (AgentSkillToolBinding declaration : declarations) {
                if (Boolean.TRUE.equals(declaration.getRequired()) && !liveToolIds.contains(declaration.getToolId())) {
                    throw new IllegalArgumentException("Required Skill tool is not available");
                }
            }
            // 多个 Skill 的声明取并集，再与 Agent 已绑定工具相交；否则不同 Skill 的独立依赖会彼此清空。
            allowedToolIds = merge(allowedToolIds, toolIds);
            Set<String> knowledgeIds = knowledgeBindingService.list(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, version.getId()))
                    .stream().map(AgentSkillKnowledgeBinding::getKnowledgeBaseId).collect(Collectors.toCollection(LinkedHashSet::new));
            allowedKnowledgeBaseIds = merge(allowedKnowledgeBaseIds, knowledgeIds);
            prompt.append("\n\n[Installed Skill]\n## ").append(skill.getName()).append(" v").append(version.getVersionNo())
                    .append("\n").append(StringUtils.defaultString(version.getInstruction()));
            if (inputs.containsKey(skill.getCode())) prompt.append("\nValidated inputs: ").append(JSON.toJSONString(inputs.get(skill.getCode())));
            Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("skillId", skill.getId()); snapshot.put("code", skill.getCode()); snapshot.put("versionId", version.getId()); snapshot.put("versionNo", version.getVersionNo()); snapshot.put("input", inputs.get(skill.getCode())); snapshotSkills.add(snapshot);
        }
        for (String code : inputs.keySet()) if (!installedCodes.contains(code)) throw new IllegalArgumentException("Skill input is not installed on this Agent");
        Set<String> finalAllowedToolIds = allowedToolIds;
        List<AgentTool> tools = toolCatalog.getBoundTools(agent.getId()).stream().filter(item -> finalAllowedToolIds != null && finalAllowedToolIds.contains(item.getId())).collect(Collectors.toList());
        prompt.append("\n\n[Platform Constraints]\n工具审批、安全与审计由平台统一控制。引用知识库资料时标注编号。");
        Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("installed", true); snapshot.put("skills", snapshotSkills); snapshot.put("toolIds", tools.stream().map(AgentTool::getId).collect(Collectors.toList())); snapshot.put("knowledgeBaseIds", allowedKnowledgeBaseIds == null ? Collections.emptySet() : allowedKnowledgeBaseIds);
        context.setInstalled(true); context.setSystemPrompt(prompt.toString()); context.setTools(tools); context.setKnowledgeBaseIds(allowedKnowledgeBaseIds == null ? Collections.emptySet() : allowedKnowledgeBaseIds); context.setSnapshot(JSON.toJSONString(snapshot));
        return context;
    }

    private Set<String> merge(Set<String> current, Set<String> next) { if (current == null) return new LinkedHashSet<>(next); current.addAll(next); return current; }
}
