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
import com.aether.agent.entity.AgentDefinition;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
    private final AgentDefinitionService agentDefinitionService = mock(AgentDefinitionService.class);
    private final AgentWorkflowCapabilityService workflowCapabilityService = mock(AgentWorkflowCapabilityService.class);
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

        JSONObject catalog = catalog(index);
        assertEquals(2, catalog.getJSONObject("categories").getJSONArray("tools").size());
        assertEquals("http", catalog.getJSONObject("categories").getJSONArray("tools").getJSONObject(0).getString("name"));
        assertEquals("HTTP 请求工具", catalog.getJSONObject("categories").getJSONArray("tools").getJSONObject(0).getString("description"));
        assertEquals(0, catalog.getJSONObject("categories").getJSONArray("skills").size());
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

        JSONObject catalog = catalog(index);
        assertEquals(2, catalog.getJSONObject("categories").getJSONArray("tools").size());
        assertEquals("generate_artifact", catalog.getJSONObject("categories").getJSONArray("tools").getJSONObject(1).getString("name"));
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

        JSONObject catalog = catalog(index);
        assertEquals("发票处理", catalog.getJSONObject("categories").getJSONArray("skills").getJSONObject(0).getString("name"));
        assertEquals("处理发票录入与审核", catalog.getJSONObject("categories").getJSONArray("skills").getJSONObject(0).getString("description"));
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

        JSONObject catalog = catalog(index);
        String description = catalog.getJSONObject("categories").getJSONArray("tools").getJSONObject(0).getString("description");
        assertTrue(description.contains("…"));
        assertTrue(description.length() <= 103);
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

        assertTrue(index.contains("\"name\":\"http\""));
        assertFalse(index.contains("dead"));
        assertFalse(index.contains("已停用的工具"));
    }

    @Test
    void includesStructuredWorkflowInputAndOutputSchemas() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("a1");
        agent.setApplicationId("app-1");
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("wf-1");
        capability.setCapabilityCode("contract_approval");
        capability.setDisplayName("合同审批");
        capability.setDescription("推进合同审批");
        capability.setAllowedActions("[\"START\",\"OBSERVE\"]");
        capability.setInputSchema("{\"type\":\"object\",\"properties\":{\"contractId\":{\"type\":\"string\"}}}");
        capability.setOutputSchema("{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\"}}}");
        when(toolCatalog.getBoundTools("a1")).thenReturn(null);
        when(agentDefinitionService.getById("a1")).thenReturn(agent);
        when(workflowCapabilityService.listEnabledForAgent("a1", "app-1"))
                .thenReturn(Collections.singletonList(capability));

        String index = new CapabilityIndexService(toolCatalog, skillService, versionService, toolLiveness,
                agentDefinitionService, workflowCapabilityService).buildIndex("a1", Collections.emptyList());

        JSONObject workflow = catalog(index).getJSONObject("categories").getJSONArray("workflows").getJSONObject(0);
        assertEquals("string", workflow.getJSONObject("inputSchema").getJSONObject("properties")
                .getJSONObject("contractId").getString("type"));
        assertEquals("string", workflow.getJSONObject("outputSchema").getJSONObject("properties")
                .getJSONObject("status").getString("type"));
        assertEquals("OBSERVE", workflow.getJSONArray("actionContracts").getJSONObject(1).getString("action"));
        assertTrue(workflow.getJSONArray("actionContracts").getJSONObject(1).getJSONArray("required")
                .contains("invocationId"));
    }

    private JSONObject catalog(String index) {
        int start = index.indexOf('{');
        return JSON.parseObject(index.substring(start));
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
