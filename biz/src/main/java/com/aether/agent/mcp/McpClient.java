package com.aether.agent.mcp;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.mcp.transport.McpTransport;
import com.aether.agent.mcp.transport.McpTransportFactory;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal MCP JSON-RPC client for Java 8 runtime.
 */
@Component
public class McpClient {

    private static final int MAX_RESPONSE_BODY = 65536;
    private static final AtomicLong ID = new AtomicLong(1);

    private final McpTransportFactory transportFactory;
    private final McpSessionManager sessionManager;

    /**
     * 创建 {@code McpClient} 实例。
     */
    public McpClient(McpTransportFactory transportFactory, McpSessionManager sessionManager) {
        this.transportFactory = transportFactory;
        this.sessionManager = sessionManager;
    }

    /**
     * 处理supportsTransport。
     */
    public boolean supportsTransport(String transportType) {
        return transportFactory.supports(transportType);
    }

    /**
     * Verifies that the configured MCP server is reachable and accepts MCP requests.
     */
    public void ping(AgentMcpServer server) {
        try {
            McpSession session = getOrInitialize(server);
            request(server, session, "ping", new JSONObject());
        } catch (Exception e) {
            sessionManager.invalidate(server.getId());
            throw new ServerException(502, I18nUtils.getMessage("agent.mcp.service.unavailable"));
        }
    }

    /**
     * 查询Tools。
     */
    public List<McpToolDefinition> listTools(AgentMcpServer server) {
        McpSession session = getOrInitialize(server);
        JSONObject result = request(server, session, "tools/list", new JSONObject());
        JSONArray tools = result.getJSONArray("tools");
        List<McpToolDefinition> definitions = new ArrayList<>();
        if (tools == null) {
            return definitions;
        }
        for (int i = 0; i < tools.size(); i++) {
            JSONObject item = tools.getJSONObject(i);
            McpToolDefinition definition = new McpToolDefinition();
            definition.setName(item.getString("name"));
            definition.setDescription(item.getString("description"));
            JSONObject inputSchema = item.getJSONObject("inputSchema");
            JSONObject outputSchema = item.getJSONObject("outputSchema");
            definition.setInputSchema(inputSchema == null ? null : inputSchema.toJSONString());
            definition.setOutputSchema(outputSchema == null ? null : outputSchema.toJSONString());
            definitions.add(definition);
        }
        return definitions;
    }

    /**
     * 处理callTool。
     */
    public JSONObject callTool(AgentMcpServer server, String toolName, Map<String, Object> arguments) {
        McpSession session = getOrInitialize(server);
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? new JSONObject() : arguments);
        return request(server, session, "tools/call", params);
    }

    /**
     * 获取OrInitialize。
     */
    private McpSession getOrInitialize(AgentMcpServer server) {
        McpSession session = sessionManager.getSession(server);
        if (session.isInitialized()) {
            return session;
        }
        synchronized (session) {
            if (!session.isInitialized()) {
                initialize(server, session);
                session.setInitialized(true);
            }
        }
        return session;
    }

    /**
     * 处理initialize。
     */
    private void initialize(AgentMcpServer server, McpSession session) {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", "2025-06-18");
        params.put("capabilities", new JSONObject());
        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "aether");
        clientInfo.put("version", "1.0-SNAPSHOT");
        params.put("clientInfo", clientInfo);

        request(server, session, "initialize", params);
        notification(server, session, "notifications/initialized");
    }

    /**
     * 处理request。
     */
    private JSONObject request(AgentMcpServer server, McpSession session, String method, JSONObject params) {
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("id", ID.getAndIncrement());
        body.put("method", method);
        body.put("params", params == null ? new JSONObject() : params);

        McpResponse response = send(server, session, body);
        JSONObject json = parseJsonRpcResponse(response.getBody());
        updateSession(session, response);
        JSONObject error = json.getJSONObject("error");
        if (error != null) {
            throw new ServerException(502, I18nUtils.getMessage("agent.mcp.call.failed"));
        }
        JSONObject result = json.getJSONObject("result");
        return result == null ? new JSONObject() : result;
    }

    /**
     * 处理notification。
     */
    private void notification(AgentMcpServer server, McpSession session, String method) {
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.put("params", new JSONObject());
        McpResponse response = send(server, session, body);
        updateSession(session, response);
    }

    /**
     * 发送当前请求。
     */
    private McpResponse send(AgentMcpServer server, McpSession session, JSONObject body) {
        try {
            McpTransport transport = transportFactory.getTransport(server.getTransport());
            return transport.send(server, session, body);
        } catch (IllegalArgumentException e) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.transport.unsupported"));
        }
    }

    /**
     * 更新会话。
     */
    private void updateSession(McpSession session, McpResponse response) {
        if (response != null && StringUtils.isNotBlank(response.getSessionId())) {
            session.setSessionId(response.getSessionId());
        }
        session.setLastAccessAt(System.currentTimeMillis());
    }

    /**
     * 解析JsonRpcResponse。
     */
    private JSONObject parseJsonRpcResponse(String body) {
        String payload = extractPayload(body);
        if (StringUtils.isBlank(payload)) {
            throw new ServerException(502, I18nUtils.getMessage("agent.mcp.response.empty"));
        }
        try {
            return JSON.parseObject(payload);
        } catch (Exception e) {
            throw new ServerException(502, I18nUtils.getMessage("agent.mcp.response.invalid"));
        }
    }

    /**
     * 处理extractPayload。
     */
    private String extractPayload(String body) {
        if (StringUtils.isBlank(body)) {
            return body;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            String item = line.trim();
            if (item.startsWith("data:")) {
                String data = item.substring("data:".length()).trim();
                if (StringUtils.isNotBlank(data) && !"[DONE]".equals(data)) {
                    return data;
                }
            }
        }
        return trimmed;
    }
}
