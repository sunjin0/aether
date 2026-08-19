package com.aether.agent.service;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证能力索引服务的构建行为。
 */
class CapabilityIndexServiceTest {

    private final AgentToolCatalog toolCatalog = mock(AgentToolCatalog.class);
    private final AgentSkillService skillService = mock(AgentSkillService.class);
    private final AgentSkillVersionServiceImpl versionService = mock(AgentSkillVersionServiceImpl.class);
    private final CapabilityIndexService service = new CapabilityIndexService(toolCatalog, skillService, versionService);

    /**
     * 处理returnsEmptyWhenNothingAvailable。
     */
    @Test
    void returnsEmptyWhenNothingAvailable() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(null);

        assertEquals("", service.buildIndex("a1", null));
        assertEquals("", service.buildIndex("a1", Collections.emptyList()));
    }

    /**
     * 处理emitsToolLinesWithNameAndDescription。
     */
    @Test
    void emitsToolLinesWithNameAndDescription() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(tool("t1", "http", "HTTP 请求工具"), tool("t2", "file", "文件读写")));

        String index = service.buildIndex("a1", Collections.emptyList());

        assertTrue(index.startsWith("\n\n[可用能力 / Available capabilities]\n"));
        assertTrue(index.contains("- http: HTTP 请求工具"));
        assertTrue(index.contains("- file: 文件读写"));
        assertFalse(index.contains("skill"));
    }

    /**
     * 处理omitsResidentToolsFromIndex。
     */
    @Test
    void omitsResidentToolsFromIndex() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(
                tool("t1", "http", "HTTP 请求工具"),
                tool("ga", "generate_artifact", "文件生成")));

        String index = service.buildIndex("a1", Collections.emptyList());

        assertTrue(index.contains("- http: HTTP 请求工具"));
        assertFalse(index.contains("generate_artifact"));
        assertFalse(index.contains("文件生成"));
    }

    /**
     * 处理emitsSkillLinesFromInstallations。
     */
    @Test
    void emitsSkillLinesFromInstallations() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(null);
        when(skillService.getById("s1")).thenReturn(skill("s1", "发票处理"));
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", "处理发票录入与审核"));

        String index = service.buildIndex("a1", Collections.singletonList(binding("a1", "s1", "v1")));

        assertTrue(index.contains("- skill 发票处理: 处理发票录入与审核"));
    }

    /**
     * 处理skipsMissingSkillOrVersionAndBlanks。
     */
    @Test
    void skipsMissingSkillOrVersionAndBlanks() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(null);
        when(skillService.getById("s1")).thenReturn(null);
        when(versionService.getById("v1")).thenReturn(version("v1", "s1", "  "));
        AgentSkillVersion blankVersion = version("v2", "s2", null);

        String index = service.buildIndex("a1", Arrays.asList(
                binding("a1", "s1", "v1"),
                binding("a1", "s2", "v2"),
                null));

        assertEquals("", index);
    }

    /**
     * 处理truncatesLongDescriptionToSingleLine。
     */
    @Test
    void truncatesLongDescriptionToSingleLine() {
        String longDescription = String.join("", Collections.nCopies(300, "字"));
        when(toolCatalog.getBoundTools("a1")).thenReturn(Collections.singletonList(tool("t1", "http", longDescription)));

        String index = service.buildIndex("a1", Collections.emptyList());

        String line = index.substring(index.indexOf("- http: "));
        assertTrue(line.contains("…"));
        assertFalse(line.contains("\n"));
        assertTrue(line.length() <= 110);
    }

    /**
     * 处理capsIndexAtBudget。
     */
    @Test
    void capsIndexAtBudget() {
        List<AgentTool> many = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            many.add(tool("t" + i, "tool-" + i, "description " + i));
        }
        when(toolCatalog.getBoundTools("a1")).thenReturn(many);

        String index = service.buildIndex("a1", Collections.emptyList());

        assertTrue(index.length() / 4 <= 1010);
        assertTrue(index.contains("tool-0"));
        assertFalse(index.contains("tool-499"));
    }

    private AgentTool tool(String id, String name, String description) {
        AgentTool tool = new AgentTool();
        tool.setId(id);
        tool.setName(name);
        tool.setDescription(description);
        tool.setMcpToolName(name);
        return tool;
    }

    private AgentDefinitionSkillBinding binding(String agentId, String skillId, String versionId) {
        AgentDefinitionSkillBinding binding = new AgentDefinitionSkillBinding();
        binding.setAgentDefinitionId(agentId);
        binding.setSkillId(skillId);
        binding.setSkillVersionId(versionId);
        return binding;
    }

    private AgentSkill skill(String id, String name) {
        AgentSkill skill = new AgentSkill();
        skill.setId(id);
        skill.setName(name);
        return skill;
    }

    private AgentSkillVersion version(String id, String skillId, String routingSummary) {
        AgentSkillVersion version = new AgentSkillVersion();
        version.setId(id);
        version.setSkillId(skillId);
        version.setRoutingSummary(routingSummary);
        return version;
    }
}
