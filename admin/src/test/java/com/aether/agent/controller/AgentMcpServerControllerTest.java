package com.aether.agent.controller;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证智能体McpServer控制器的行为。
 */
class AgentMcpServerControllerTest {

    /**
     * 处理setUpI18n。
     */
    @BeforeEach
    void setUpI18n() {
        new I18nUtils(mock(I18nService.class));
    }

    /**
     * 查询ToolsPingsServerBeforeDiscoveringTools。
     */
    @Test
    void listToolsPingsServerBeforeDiscoveringTools() {
        AgentMcpServerService serverService = mock(AgentMcpServerService.class);
        McpClient mcpClient = mock(McpClient.class);
        AgentMcpServer server = enabledServer();
        when(serverService.getById("server-1")).thenReturn(server);
        when(mcpClient.supportsTransport("http")).thenReturn(true);

        AgentMcpServerController controller = new AgentMcpServerController(
                serverService, mock(AgentToolService.class), mcpClient);

        controller.listTools("server-1");

        verify(mcpClient).ping(server);
        verify(mcpClient).listTools(server);
    }

    /**
     * 查询ToolsDoesNotDiscoverWhenPingFails。
     */
    @Test
    void listToolsDoesNotDiscoverWhenPingFails() {
        AgentMcpServerService serverService = mock(AgentMcpServerService.class);
        McpClient mcpClient = mock(McpClient.class);
        AgentMcpServer server = enabledServer();
        when(serverService.getById("server-1")).thenReturn(server);
        when(mcpClient.supportsTransport("http")).thenReturn(true);
        doThrow(new ServerException(502, "MCP服务不可用，请检查服务状态或连接配置"))
                .when(mcpClient).ping(server);

        AgentMcpServerController controller = new AgentMcpServerController(
                serverService, mock(AgentToolService.class), mcpClient);

        assertThrows(ServerException.class, () -> controller.listTools("server-1"));

        verify(mcpClient, never()).listTools(any());
    }

    /**
     * 处理enabledServer。
     */
    private AgentMcpServer enabledServer() {
        AgentMcpServer server = new AgentMcpServer();
        server.setId("server-1");
        server.setBaseUrl("http://localhost:3000/mcp");
        server.setTransport("http");
        server.setStatus(1);
        server.setDeleted(false);
        return server;
    }
}
