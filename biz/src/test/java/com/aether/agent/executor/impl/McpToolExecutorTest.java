package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.DelegationTokenService;
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

/**
 * 验证McpToolExecutor的行为。
 */
class McpToolExecutorTest {

    /**
 * 执行ReturnsUnavailable消息WhenPingFails。
 */
@Test
    void executeReturnsUnavailableMessageWhenPingFails() {
        McpClient mcpClient = mock(McpClient.class);
        AgentMcpServerService serverService = mock(AgentMcpServerService.class);
        DelegationTokenService delegationTokenService = mock(DelegationTokenService.class);
        AgentMcpServer server = server();
        when(serverService.getById("server-1")).thenReturn(server);
        when(mcpClient.supportsTransport("http")).thenReturn(true);
        when(delegationTokenService.create(any(), any(), any(), any(), any(), any(), any())).thenReturn("delegation-token");
        doThrow(new ServerException(502, "MCP服务不可用，请检查服务状态或连接配置"))
                .when(mcpClient).ping(server);

        McpToolExecutor executor = new McpToolExecutor(mcpClient, serverService, delegationTokenService);
        ToolExecutionResult result = executor.execute(context());

        assertFalse(result.isSuccess());
        assertEquals("502:MCP服务不可用，请检查服务状态或连接配置", result.getErrorMsg());
        verify(mcpClient, never()).callTool(any(), any(), any());
    }

    /**
 * 处理server。
 */
private AgentMcpServer server() {
        AgentMcpServer server = new AgentMcpServer();
        server.setId("server-1");
        server.setBaseUrl("http://localhost:3000/mcp");
        server.setTransport("http");
        server.setStatus(1);
        server.setDeleted(false);
        return server;
    }

    /**
 * 处理context。
 */
private ToolExecutionContext context() {
        AgentTool tool = new AgentTool();
        tool.setName("search");
        tool.setMcpServerId("server-1");
        ToolExecutionContext context = new ToolExecutionContext();
        context.setTool(tool);
        context.setArguments(Collections.emptyMap());
        context.setRunId("run-1");
        context.setUserId("user-1");
        context.setAgentDefinitionId("agent-1");
        return context;
    }
}
