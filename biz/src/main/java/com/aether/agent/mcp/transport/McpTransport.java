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

    /** 发送带请求作用域连接器凭据的 MCP 请求；默认实现保持旧 Transport 兼容。 */
    default McpResponse send(AgentMcpServer server, McpSession session, JSONObject body,
                             String connectorCredentialToken) {
        return send(server, session, body);
    }
}
