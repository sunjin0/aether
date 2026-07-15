package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.exception.ServerException;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolExecutorTest {

    @Test
    void executeReturnsUnavailableMessageWhenPingFails() {
        McpClient mcpClient = mock(McpClient.class);
        AgentMcpServerService serverService = mock(AgentMcpServerService.class);
        AgentMcpServer server = server();
        when(serverService.getById("server-1")).thenReturn(server);
        when(mcpClient.supportsTransport("http")).thenReturn(true);
        doThrow(new ServerException(502, "MCP服务不可用，请检查服务状态或连接配置"))
                .when(mcpClient).ping(server);

        McpToolExecutor executor = new McpToolExecutor(mcpClient, serverService);
        ToolExecutionResult result = executor.execute(context());

        assertFalse(result.isSuccess());
        assertEquals("502:MCP服务不可用，请检查服务状态或连接配置", result.getErrorMsg());
        verify(mcpClient, never()).callTool(any(), any(), any());
    }

    private AgentMcpServer server() {
        AgentMcpServer server = new AgentMcpServer();
        server.setId("server-1");
        server.setBaseUrl("http://localhost:3000/mcp");
        server.setTransport("http");
        server.setStatus(1);
        server.setDeleted(false);
        return server;
    }

    private ToolExecutionContext context() {
        AgentTool tool = new AgentTool();
        tool.setName("search");
        tool.setMcpServerId("server-1");
        ToolExecutionContext context = new ToolExecutionContext();
        context.setTool(tool);
        context.setArguments(Collections.emptyMap());
        return context;
    }
}
