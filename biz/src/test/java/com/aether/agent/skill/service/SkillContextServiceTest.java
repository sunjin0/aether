package com.aether.agent.skill.service;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillKnowledgeBinding;
import com.aether.agent.skill.entity.AgentSkillToolBinding;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillKnowledgeBindingServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillToolBindingServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillContextServiceTest {

    private final AgentSkillService skillService = mock(AgentSkillService.class);
    private final AgentSkillVersionServiceImpl versionService = mock(AgentSkillVersionServiceImpl.class);
    private final AgentSkillToolBindingServiceImpl toolBindingService = mock(AgentSkillToolBindingServiceImpl.class);
    private final AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService = mock(AgentSkillKnowledgeBindingServiceImpl.class);
    private final AgentToolCatalog toolCatalog = mock(AgentToolCatalog.class);
    private final SkillContextService service = new SkillContextService(skillService, versionService, toolBindingService, knowledgeBindingService, toolCatalog);

    @Test
    void noInstallationFallsBackToAgentDefaults() {
        AgentDefinition agent = agent("a1", "base prompt");
        when(skillService.listBindings("a1")).thenReturn(Collections.emptyList());
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(tool("t1", "Tool A", 1), tool("t2", "Tool B", 1)));

        SkillRuntimeContext context = service.resolve(agent, new AgentChatDto());

        assertFalse(context.isInstalled());
        assertEquals("base prompt", context.getSystemPrompt());
        assertEquals(2, context.getTools().size());
        assertNull(context.getKnowledgeBaseIds());
        assertTrue(context.getSnapshot().contains("\"installed\":false"));
    }

    @Test
    void rejectsMoreThanThreeInstalledSkills() {
        AgentDefinition agent = agent("a1", "p");
        when(skillService.listBindings("a1")).thenReturn(Arrays.asList(
                binding("a1", "s1", "v1", 1, 1),
                binding("a1", "s2", "v2", 2, 1),
                binding("a1", "s3", "v3", 3, 1),
                binding("a1", "s4", "v4", 4, 1)
        ));

        assertThrows(IllegalArgumentException.class, () -> service.resolve(agent, new AgentChatDto()));
    }

    @Test
    void rejectsDisabledSkillOrUnpublishedVersion() {
        AgentDefinition agent = agent("a1", "p");
        when(skillService.listBindings("a1")).thenReturn(Collections.singletonList(binding("a1", "s1", "v1", 1, 1)));
        when(skillService.getById("s1")).thenReturn(skill("s1", "s1c", "S1", 0));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", 1, 1));

        assertThrows(IllegalArgumentException.class, () -> service.resolve(agent, new AgentChatDto()));

        when(skillService.getById("s1")).thenReturn(skill("s1", "s1c", "S1", 1));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(agent, new AgentChatDto()));
    }

    @Test
    void rejectsMissingRequiredTool() {
        AgentDefinition agent = agent("a1", "p");
        when(skillService.listBindings("a1")).thenReturn(Collections.singletonList(binding("a1", "s1", "v1", 1, 1)));
        when(skillService.getById("s1")).thenReturn(skill("s1", "s1c", "S1", 1));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", 1, 1));
        when(toolBindingService.list(any())).thenReturn(Collections.singletonList(toolBinding("v1", "tMissing", true, 0)));
        when(toolCatalog.getBoundTools("a1")).thenReturn(Collections.singletonList(tool("tOther", "Other", 1)));

        assertThrows(IllegalArgumentException.class, () -> service.resolve(agent, new AgentChatDto()));
    }

    @Test
    void narrowsToolsToDeclaredIntersection() {
        AgentDefinition agent = agent("a1", "p");
        when(skillService.listBindings("a1")).thenReturn(Collections.singletonList(binding("a1", "s1", "v1", 1, 1)));
        when(skillService.getById("s1")).thenReturn(skill("s1", "s1c", "S1", 1));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", 1, 1));
        when(toolBindingService.list(any())).thenReturn(Arrays.asList(toolBinding("v1", "t1", true, 0), toolBinding("v1", "tNotBound", false, 0)));
        when(knowledgeBindingService.list(any())).thenReturn(Collections.singletonList(kbBinding("v1", "kb1")));
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(tool("t1", "Tool A", 1), tool("tNotBound", "Tool B", 1), tool("tUndeclared", "Tool C", 1)));

        SkillRuntimeContext context = service.resolve(agent, new AgentChatDto());

        assertTrue(context.isInstalled());
        assertEquals(2, context.getTools().size());
        assertTrue(context.getTools().stream().anyMatch(t -> "t1".equals(t.getId())));
        assertTrue(context.getTools().stream().anyMatch(t -> "tNotBound".equals(t.getId())));
        assertTrue(context.getTools().stream().noneMatch(t -> "tUndeclared".equals(t.getId())));
        assertTrue(context.getKnowledgeBaseIds().contains("kb1"));
        assertTrue(context.getSystemPrompt().contains("[Installed Skill]"));
        assertTrue(context.getSystemPrompt().contains("[Platform Constraints]"));
    }

    @Test
    void rejectsInputsForUninstalledSkillCode() {
        AgentDefinition agent = agent("a1", "p");
        when(skillService.listBindings("a1")).thenReturn(Collections.singletonList(binding("a1", "s1", "v1", 1, 1)));
        when(skillService.getById("s1")).thenReturn(skill("s1", "s1c", "S1", 1));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", 1, 1));
        when(toolBindingService.list(any())).thenReturn(Collections.emptyList());
        when(knowledgeBindingService.list(any())).thenReturn(Collections.emptyList());
        when(toolCatalog.getBoundTools("a1")).thenReturn(Collections.emptyList());

        AgentChatDto dto = new AgentChatDto();
        Map<String, Map<String, Object>> inputs = new HashMap<>();
        Map<String, Object> sample = new HashMap<>();
        sample.put("q", "hello");
        inputs.put("not-installed-code", sample);
        dto.setSkillInputs(inputs);

        assertThrows(IllegalArgumentException.class, () -> service.resolve(agent, dto));
    }

    @Test
    void mergesToolDeclarationsAcrossSkillsBeforeIntersection() {
        AgentDefinition agent = agent("a1", "p");
        when(skillService.listBindings("a1")).thenReturn(Arrays.asList(
                binding("a1", "s1", "v1", 1, 1),
                binding("a1", "s2", "v2", 2, 1)
        ));
        when(skillService.getById("s1")).thenReturn(skill("s1", "s1c", "S1", 1));
        when(skillService.getById("s2")).thenReturn(skill("s2", "s2c", "S2", 1));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", 1, 1));
        when(versionService.getById("v2")).thenReturn(version("v2", "s2", 1, 1));
        when(toolBindingService.list(any()))
                .thenReturn(Collections.singletonList(toolBinding("v1", "t1", true, 0)))
                .thenReturn(Collections.singletonList(toolBinding("v2", "t2", true, 0)));
        when(knowledgeBindingService.list(any())).thenReturn(Collections.emptyList());
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(tool("t1", "Tool A", 1), tool("t2", "Tool B", 1)));

        SkillRuntimeContext context = service.resolve(agent, new AgentChatDto());

        assertEquals(2, context.getTools().size());
    }

    private AgentDefinition agent(String id, String systemPrompt) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(id);
        agent.setSystemPrompt(systemPrompt);
        return agent;
    }

    private AgentSkill skill(String id, String code, String name, int status) {
        AgentSkill skill = new AgentSkill();
        skill.setId(id);
        skill.setCode(code);
        skill.setName(name);
        skill.setStatus(status);
        return skill;
    }

    private AgentSkillVersion version(String id, String skillId, int versionNo, int status) {
        AgentSkillVersion version = new AgentSkillVersion();
        version.setId(id);
        version.setSkillId(skillId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setInstruction("instruction");
        return version;
    }

    private AgentDefinitionSkillBinding binding(String agentId, String skillId, String versionId, int priority, int status) {
        AgentDefinitionSkillBinding binding = new AgentDefinitionSkillBinding();
        binding.setAgentDefinitionId(agentId);
        binding.setSkillId(skillId);
        binding.setSkillVersionId(versionId);
        binding.setPriority(priority);
        binding.setStatus(status);
        return binding;
    }

    private AgentSkillToolBinding toolBinding(String versionId, String toolId, boolean required, int priority) {
        AgentSkillToolBinding binding = new AgentSkillToolBinding();
        binding.setSkillVersionId(versionId);
        binding.setToolId(toolId);
        binding.setRequired(required);
        binding.setPriority(priority);
        return binding;
    }

    private AgentSkillKnowledgeBinding kbBinding(String versionId, String kbId) {
        AgentSkillKnowledgeBinding binding = new AgentSkillKnowledgeBinding();
        binding.setSkillVersionId(versionId);
        binding.setKnowledgeBaseId(kbId);
        return binding;
    }

    private AgentTool tool(String id, String name, int status) {
        AgentTool tool = new AgentTool();
        tool.setId(id);
        tool.setName(name);
        tool.setStatus(status);
        tool.setDeleted(false);
        return tool;
    }
}
