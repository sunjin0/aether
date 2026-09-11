package com.aether.agent.service;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.tools.AgentToolLiveness;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

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
    private final AgentMcpServerService mcpServerService = mock(AgentMcpServerService.class);
    private final AgentToolLiveness toolLiveness = new AgentToolLiveness(mcpServerService);
    private final CapabilityIndexService service = new CapabilityIndexService(toolCatalog, skillService, versionService, toolLiveness);

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
        assertTrue(index.contains("- tool http: HTTP 请求工具"));
        assertTrue(index.contains("- tool file: 文件读写"));
        assertFalse(index.contains("skill"));
    }

    /**
     * 处理omitsResidentToolsFromIndex。
     */
    @Test
    void includesAllBoundToolsInIndex() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(
                tool("t1", "http", "HTTP 请求工具"),
                tool("ga", "generate_artifact", "文件生成")));

        String index = service.buildIndex("a1", Collections.emptyList());

        assertTrue(index.contains("- tool http: HTTP 请求工具"));
        assertTrue(index.contains("generate_artifact"));
        assertTrue(index.contains("文件生成"));
    }

    /**
     * 处理emitsSkillLinesFromInstallations。
     */
    @Test
    void emitsSkillLinesFromInstallations() {
        when(toolCatalog.getBoundTools("a1")).thenReturn(null);
        when(skillService.getById("s1")).thenReturn(skill("s1", "发票处理", "处理发票录入与审核"));
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

        String line = index.substring(index.indexOf("- tool http: "));
        assertTrue(line.contains("…"));
        assertFalse(line.contains("\n"));
        assertTrue(line.length() <= 220);
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

    /**
     * 回归：MCP 服务已停用的工具不应出现在常驻能力索引里——索引与"可用条件：MCP 服务启用"
     * 的措辞必须一致，否则模型会据索引认定一个实际调用必然失败的能力。
     */
    @Test
    void omitsToolsWhoseMcpServerIsDisabled() {
        when(mcpServerService.getById("mcp-on")).thenReturn(server("mcp-on", 1));
        when(mcpServerService.getById("mcp-off")).thenReturn(server("mcp-off", 0));
        // 批量过滤按 id 取服务；这里回落到 getById 的桩，行为与逐条查询一致。
        when(mcpServerService.listByIds(ArgumentMatchers.anyCollection())).thenAnswer(invocation -> {
            List<AgentMcpServer> found = new java.util.ArrayList<>();
            for (Object id : invocation.<java.util.Collection<?>>getArgument(0)) {
                AgentMcpServer server = mcpServerService.getById(String.valueOf(id));
                if (server != null) found.add(server);
            }
            return found;
        });
        when(toolCatalog.getBoundTools("a1")).thenReturn(Arrays.asList(
                toolOnServer("t1", "http", "HTTP 请求工具", "mcp-on"),
                toolOnServer("t2", "dead", "已停用的工具", "mcp-off")));

        String index = service.buildIndex("a1", Collections.emptyList());

        assertTrue(index.contains("- tool http: HTTP 请求工具"));
        assertFalse(index.contains("dead"));
        assertFalse(index.contains("已停用的工具"));
    }

    private AgentMcpServer server(String id, int status) {
        AgentMcpServer server = new AgentMcpServer();
        server.setId(id);
        server.setStatus(status);
        server.setDeleted(false);
        return server;
    }

    private AgentTool toolOnServer(String id, String name, String description, String serverId) {
        AgentTool tool = tool(id, name, description);
        tool.setMcpServerId(serverId);
        return tool;
    }

    /** 与 AgentToolCatalog.getBoundTools 的产出对齐：启用、未删除。无 mcpServerId 即内置工具。 */
    private AgentTool tool(String id, String name, String description) {
        AgentTool tool = new AgentTool();
        tool.setId(id);
        tool.setName(name);
        tool.setDescription(description);
        tool.setMcpToolName(name);
        tool.setStatus(1);
        tool.setDeleted(false);
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
        return skill(id, name, null);
    }

    private AgentSkill skill(String id, String name, String description) {
        AgentSkill skill = new AgentSkill();
        skill.setId(id);
        skill.setName(name);
        skill.setDescription(description);
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
