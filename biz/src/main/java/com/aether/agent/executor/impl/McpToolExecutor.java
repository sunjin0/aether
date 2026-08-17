package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.DelegationTokenService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool executor. AgentTool stores the concrete MCP tool name and references an MCP server configuration.
 */
@Component
public class McpToolExecutor implements ToolExecutor {

    private static final int MAX_RESPONSE_BODY = 65536;

    private final McpClient mcpClient;
    private final AgentMcpServerService agentMcpServerService;
    private final DelegationTokenService delegationTokenService;

    /**
     * 创建 {@code McpToolExecutor} 实例。
     */
    public McpToolExecutor(McpClient mcpClient, AgentMcpServerService agentMcpServerService,
                           DelegationTokenService delegationTokenService) {
        this.mcpClient = mcpClient;
        this.agentMcpServerService = agentMcpServerService;
        this.delegationTokenService = delegationTokenService;
    }

    /**
     * 处理supports。
     */
    @Override
    public boolean supports(String toolType) {
        return StringUtils.isBlank(toolType) || "mcp".equalsIgnoreCase(toolType);
    }

    /**
     * 执行当前请求。
     */
    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        AgentTool tool = context.getTool();
        long startTime = System.currentTimeMillis();
        String requestBody = null;
        String requestUrl = null;
        try {
            AgentMcpServer server = buildServer(tool);
            String delegationToken = applyDelegationToken(server, context, tool);
            requestUrl = server.getBaseUrl();
            String mcpToolName = StringUtils.defaultIfBlank(tool.getMcpToolName(), tool.getName());
            Map<String, Object> arguments = context.getArguments() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context.getArguments());
            // FastMCP runs tool workers out of the inbound ASGI context.  This
            // opaque five-minute token is injected by Java only; Admin verifies
            // it again and never accepts a caller-provided command or image.
            if ("generate_artifact".equals(mcpToolName)) arguments.put("aether_delegation", delegationToken);
            Map<String, Object> auditArguments = new LinkedHashMap<>(arguments);
            if (auditArguments.containsKey("aether_delegation")) auditArguments.put("aether_delegation", "***");
            requestBody = JSON.toJSONString(auditArguments);
            mcpClient.ping(server);
            JSONObject response = mcpClient.callTool(server, mcpToolName, arguments);
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
            result.setRequestHeaders(maskAuthorization(server.getRequestHeaders()));
            result.setRequestBody(requestBody);
            return result;
        } catch (ServerException e) {
            return failure(requestUrl, requestBody, e.getMessage(), startTime);
        } catch (Exception e) {
            return failure(requestUrl, requestBody, I18nUtils.getMessage("agent.mcp.tool.execution.failed"), startTime);
        }
    }

    /**
     * 构建Server。
     */
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

    /**
     * 普通 Agent 与 Deep Agent 使用相同的 Java 委派 JWT，MCP 仅接受当前运行允许的工具。
     */
    private String applyDelegationToken(AgentMcpServer server, ToolExecutionContext context, AgentTool tool) {
        if (StringUtils.isAnyBlank(context.getRunId(), context.getUserId(), context.getAgentDefinitionId())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.delegation-context.required"));
        }
        String toolName = StringUtils.defaultIfBlank(tool.getMcpToolName(), tool.getName());
        String token = delegationTokenService.create(context.getRunId(), context.getUserId(),
                context.getAgentDefinitionId(), Collections.singletonList(toolName));
        JSONObject headers = StringUtils.isBlank(server.getRequestHeaders())
                ? new JSONObject() : JSON.parseObject(server.getRequestHeaders());
        headers.put("Authorization", "Bearer " + token);
        if (StringUtils.isNotBlank(context.getIdempotencyKey())) {
            headers.put("X-Aether-Idempotency-Key", context.getIdempotencyKey());
        }
        // 仅修改当前内存对象，绝不写回数据库或复用为长期静态令牌。
        server.setRequestHeaders(headers.toJSONString());
        return token;
    }

    /**
     * 处理maskAuthorization。
     */
    private String maskAuthorization(String headersJson) {
        if (StringUtils.isBlank(headersJson)) {
            return headersJson;
        }
        try {
            JSONObject headers = JSON.parseObject(headersJson);
            for (String key : headers.keySet()) {
                if ("authorization".equalsIgnoreCase(key)) {
                    headers.put(key, "Bearer ***");
                }
            }
            return headers.toJSONString();
        } catch (Exception ignored) {
            return "{\"Authorization\":\"***\"}";
        }
    }

    /**
     * 校验Server。
     */
    private void validateServer(AgentMcpServer server) {
        if (StringUtils.isBlank(server.getBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.endpoint.required"));
        }
        if (!mcpClient.supportsTransport(server.getTransport())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.mcp.transport.unsupported"));
        }
    }

    /**
     * 处理extractContent。
     */
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

    /**
     * 处理failure。
     */
    private ToolExecutionResult failure(String requestUrl, String requestBody, String message, long startTime) {
        ToolExecutionResult result = ToolExecutionResult.failure(message, 1);
        result.setRequestUrl(requestUrl);
        result.setRequestMethod("MCP tools/call");
        result.setRequestBody(requestBody);
        result.setLatencyMs((int) (System.currentTimeMillis() - startTime));
        return result;
    }

    /**
     * 处理truncate。
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
