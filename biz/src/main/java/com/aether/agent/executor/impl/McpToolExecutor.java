package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * MCP tool executor. AgentTool stores the concrete MCP tool name and references an MCP server configuration.
 */
@Component
public class McpToolExecutor implements ToolExecutor {

    private static final int MAX_RESPONSE_BODY = 65536;

    private final McpClient mcpClient;
    private final AgentMcpServerService agentMcpServerService;

    public McpToolExecutor(McpClient mcpClient, AgentMcpServerService agentMcpServerService) {
        this.mcpClient = mcpClient;
        this.agentMcpServerService = agentMcpServerService;
    }

    @Override
    public boolean supports(String toolType) {
        return StringUtils.isBlank(toolType) || "mcp".equalsIgnoreCase(toolType);
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        AgentTool tool = context.getTool();
        long startTime = System.currentTimeMillis();
        String requestBody = null;
        String requestUrl = null;
        try {
            AgentMcpServer server = buildServer(tool);
            requestUrl = server.getBaseUrl();
            String mcpToolName = StringUtils.defaultIfBlank(tool.getMcpToolName(), tool.getName());
            requestBody = JSON.toJSONString(context.getArguments());
            mcpClient.ping(server);
            JSONObject response = mcpClient.callTool(server, mcpToolName, context.getArguments());
            long latencyMs = System.currentTimeMillis() - startTime;

            boolean mcpError = Boolean.TRUE.equals(response.getBoolean("isError"));
            ToolExecutionResult result = mcpError
                    ? ToolExecutionResult.failure(truncate(extractContent(response), 4096), 1)
                    : ToolExecutionResult.success(
                            truncate(extractContent(response), 4096),
                            truncate(response.toJSONString(), MAX_RESPONSE_BODY),
                            200,
                            (int) latencyMs
                    );
            if (mcpError) {
                result.setRawResponse(truncate(response.toJSONString(), MAX_RESPONSE_BODY));
                result.setHttpStatus(200);
                result.setLatencyMs((int) latencyMs);
            }
            result.setRequestUrl(server.getBaseUrl());
            result.setRequestMethod("MCP tools/call");
            result.setRequestHeaders(server.getRequestHeaders());
            result.setRequestBody(requestBody);
            return result;
        } catch (ServerException e) {
            return failure(requestUrl, requestBody, e.getMessage(), startTime);
        } catch (Exception e) {
            return failure(requestUrl, requestBody, I18nUtils.getMessage("agent.mcp.tool.execution.failed"), startTime);
        }
    }

    private AgentMcpServer buildServer(AgentTool tool) {
        if (StringUtils.isBlank(tool.getMcpServerId())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.service.required"));
        }
        AgentMcpServer server = agentMcpServerService.getById(tool.getMcpServerId());
        if (server == null || Boolean.TRUE.equals(server.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.mcp.service.not.found"));
        }
        if (!Integer.valueOf(1).equals(server.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.service.disabled"));
        }
        if (tool.getTimeoutMs() != null) {
            server.setTimeoutMs(tool.getTimeoutMs());
        }
        validateServer(server);
        return server;
    }

    private void validateServer(AgentMcpServer server) {
        if (StringUtils.isBlank(server.getBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.endpoint.required"));
        }
        if (!mcpClient.supportsTransport(server.getTransport())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.transport.unsupported"));
        }
    }

    private String extractContent(JSONObject response) {
        if (response == null) {
            return "";
        }
        if (Boolean.TRUE.equals(response.getBoolean("isError"))) {
            return response.toJSONString();
        }
        JSONArray content = response.getJSONArray("content");
        if (content == null || content.isEmpty()) {
            JSONObject structuredContent = response.getJSONObject("structuredContent");
            return structuredContent == null ? response.toJSONString() : structuredContent.toJSONString();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JSONObject item = content.getJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("text".equalsIgnoreCase(item.getString("type"))) {
                builder.append(item.getString("text"));
            } else {
                builder.append(item.toJSONString());
            }
            if (i < content.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private ToolExecutionResult failure(String requestUrl, String requestBody, String message, long startTime) {
        ToolExecutionResult result = ToolExecutionResult.failure(message, 1);
        result.setRequestUrl(requestUrl);
        result.setRequestMethod("MCP tools/call");
        result.setRequestBody(requestBody);
        result.setLatencyMs((int) (System.currentTimeMillis() - startTime));
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
