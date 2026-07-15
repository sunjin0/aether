package com.aether.agent.controller;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutorFactory;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentToolFacetsVo;
import com.aether.entity.Option;
import com.aether.entity.WebResponse;
import com.aether.sys.service.DictService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolControllerTest {

    @Test
    void facetsAggregatesToolsWithDictionaryAndMcpServerLabels() {
        AgentToolService toolService = mock(AgentToolService.class);
        AgentMcpServerService mcpServerService = mock(AgentMcpServerService.class);
        DictService dictService = mock(DictService.class);
        when(toolService.list(any())).thenReturn(Arrays.asList(
                tool("1", "knowledge", "100", 1),
                tool("2", "knowledge", "100", 0),
                tool("3", "ops", "200", 1),
                tool("4", "general", null, 1)
        ));
        when(dictService.getOptions("Agent_Tool_Business_Type", true)).thenReturn(Arrays.asList(
                new Option("知识库", "knowledge"),
                new Option("运维监控", "ops")
        ));
        when(mcpServerService.list(any())).thenReturn(Arrays.asList(
                mcpServer("100", "Knowledge MCP"),
                mcpServer("200", "Operations MCP")
        ));

        AgentToolController controller = new AgentToolController(
                toolService,
                mock(AgentToolCallLogService.class),
                mcpServerService,
                mock(ToolExecutorFactory.class),
                dictService
        );

        WebResponse<AgentToolFacetsVo> response = controller.facets();

        assertEquals(200, response.getCode());
        assertFacet(response.getData().getCategories(), "knowledge", "知识库", 2L);
        assertFacet(response.getData().getCategories(), "ops", "运维监控", 1L);
        assertFacet(response.getData().getCategories(), "general", "general", 1L);
        assertFacet(response.getData().getStatuses(), 1, "已集成", 3L);
        assertFacet(response.getData().getStatuses(), 0, "未集成", 1L);
        assertFacet(response.getData().getSources(), "100", "Knowledge MCP", 2L);
        assertFacet(response.getData().getSources(), "200", "Operations MCP", 1L);
        assertFacet(response.getData().getSources(), "none", "无来源", 1L);
    }

    @Test
    void facetsExcludeSourcesWhoseMcpServerIsUnavailable() {
        AgentToolService toolService = mock(AgentToolService.class);
        AgentMcpServerService mcpServerService = mock(AgentMcpServerService.class);
        DictService dictService = mock(DictService.class);
        when(toolService.list(any())).thenReturn(Arrays.asList(
                tool("1", "knowledge", "100", 1),
                tool("2", "knowledge", "missing", 1),
                tool("3", "knowledge", null, 1)
        ));
        when(dictService.getOptions("Agent_Tool_Business_Type", true)).thenReturn(
                Arrays.asList(new Option("知识库", "knowledge")));
        when(mcpServerService.list(any())).thenReturn(Arrays.asList(mcpServer("100", "Knowledge MCP")));

        AgentToolController controller = new AgentToolController(
                toolService,
                mock(AgentToolCallLogService.class),
                mcpServerService,
                mock(ToolExecutorFactory.class),
                dictService
        );

        List<AgentToolFacetsVo.Item> sources = controller.facets().getData().getSources();

        assertEquals(2, sources.size());
        assertFacet(sources, "100", "Knowledge MCP", 1L);
        assertFacet(sources, "none", "无来源", 1L);
    }

    private AgentTool tool(String id, String type, String serverId, int status) {
        AgentTool tool = new AgentTool();
        tool.setId(id);
        tool.setToolType(type);
        tool.setMcpServerId(serverId);
        tool.setStatus(status);
        tool.setDeleted(false);
        return tool;
    }

    private AgentMcpServer mcpServer(String id, String name) {
        AgentMcpServer server = new AgentMcpServer();
        server.setId(id);
        server.setName(name);
        server.setDeleted(false);
        return server;
    }

    private void assertFacet(List<AgentToolFacetsVo.Item> items, Object value, String label, long count) {
        AgentToolFacetsVo.Item item = items.stream()
                .filter(candidate -> value.equals(candidate.getValue()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals(label, item.getLabel());
        assertEquals(count, item.getCount());
    }
}
