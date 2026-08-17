package com.aether.agent.mcp.transport;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.mcp.McpResponse;
import com.aether.agent.mcp.McpSession;
import com.alibaba.fastjson2.JSONObject;

/**
 * MCP transport abstraction.
 */
public interface McpTransport {

    /**
     * 处理supports。
     */
    boolean supports(String transport);

    /**
     * 发送当前请求。
     */
    McpResponse send(AgentMcpServer server, McpSession session, JSONObject body);
}
