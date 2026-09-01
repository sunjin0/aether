package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.DelegationTokenService;
import com.aether.agent.service.RuntimeEmailCredentialStore;
import com.aether.agent.service.EmailCredentialTokenService;
import com.aether.agent.service.ConnectorCredentialTokenService;
import com.aether.governance.service.SecretProvider;
import com.aether.sys.service.UserService;
import com.aether.sys.entity.User;
import com.aether.utils.AesUtil;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * MCP tool executor. AgentTool stores the concrete MCP tool name and references an MCP server configuration.
 */
@Component
public class McpToolExecutor implements ToolExecutor {

    private static final int MAX_RESPONSE_BODY = 65536;

    private final McpClient mcpClient;
    private final AgentMcpServerService agentMcpServerService;
    private final DelegationTokenService delegationTokenService;
    @Autowired(required = false)
    private RuntimeEmailCredentialStore runtimeEmailCredentialStore;
    @Autowired(required = false)
    private EmailCredentialTokenService emailCredentialTokenService;
    @Autowired(required = false)
    private ConnectorCredentialTokenService connectorCredentialTokenService;
    @Autowired(required = false)
    private SecretProvider secretProvider;
    @Autowired(required = false)
    private UserService userService;
    @Autowired(required = false)
    private AgentDefinitionService agentDefinitionService;

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
        checkCancelled(context);
        AgentTool tool = context.getTool();
        long startTime = System.currentTimeMillis();
        String requestBody = null;
        String requestUrl = null;
        try {
            AgentMcpServer server = buildServer(tool);
            String delegationToken = applyDelegationToken(server, context, tool);
            requestUrl = server.getBaseUrl();
            String mcpToolName = StringUtils.defaultIfBlank(tool.getMcpToolName(), tool.getName());
            String connectorCredentialToken = createConnectorCredentialToken(server, context, mcpToolName);
            Map<String, Object> arguments = context.getArguments() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context.getArguments());
            Map<String, Object> trustedContext = applyTrustedContext(context, arguments);
            sanitizeEmailArguments(mcpToolName, arguments);
            // FastMCP runs tool workers out of the inbound ASGI context.  This
            // opaque five-minute token is injected by Java only; Admin verifies
            // it again and never accepts a caller-provided command or image.
            if ("generate_artifact".equals(mcpToolName)) arguments.put("aether_delegation", delegationToken);
            Map<String, Object> auditArguments = new LinkedHashMap<>(arguments);
            for (String key : trustedContext.keySet()) if (auditArguments.containsKey(key)) auditArguments.put(key, "***");
            if (auditArguments.containsKey("aether_delegation")) auditArguments.put("aether_delegation", "***");
            requestBody = JSON.toJSONString(auditArguments);
            checkCancelled(context);
            mcpClient.ping(server);
            checkCancelled(context);
            JSONObject response = mcpClient.callTool(server, mcpToolName, arguments, connectorCredentialToken);
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
        } catch (CancellationException e) {
            throw e;
        } catch (ServerException e) {
            return failure(requestUrl, requestBody, e.getMessage(), startTime);
        } catch (Exception e) {
            return failure(requestUrl, requestBody, I18nUtils.getMessage("agent.mcp.tool.execution.failed"), startTime);
        }
    }

    /** 为官方只读运维连接器生成请求级凭据令牌；未配置凭据时不使用静态回退。 */
    private String createConnectorCredentialToken(AgentMcpServer server, ToolExecutionContext context, String toolName) {
        if (!isOfficialConnectorTool(toolName)) return null;
        if (StringUtils.isAnyBlank(server.getCredentialRef(), server.getId(), server.getTenantId())
                || secretProvider == null || connectorCredentialTokenService == null) {
            throw new ServerException(422, "连接器临时凭据未配置");
        }
        Map<String, String> credential = secretProvider.resolve(server.getCredentialRef(), "tenant", server.getTenantId());
        if (credential == null || credential.isEmpty()) throw new ServerException(422, "连接器临时凭据不存在");
        return connectorCredentialTokenService.create(context.getRunId(), context.getUserId(), server.getTenantId(),
                server.getId(), Collections.singletonList(toolName), credential);
    }

    private boolean isOfficialConnectorTool(String toolName) {
        return "prometheus_query".equals(toolName) || "grafana_query".equals(toolName)
                || "kubernetes_get_pods".equals(toolName);
    }

    /** 在发起外部 MCP 请求前检查取消状态，避免断开连接后继续产生副作用。 */
    private void checkCancelled(ToolExecutionContext context) {
        if (context != null && context.getCancellationToken() != null) {
            context.getCancellationToken().throwIfCancelled();
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
                context.getAgentDefinitionId(), Collections.singletonList(toolName), context.getApplicationId(),
                context.getProductProfileId(), context.getServiceAccountId());
        JSONObject headers = StringUtils.isBlank(server.getRequestHeaders())
                ? new JSONObject() : JSON.parseObject(server.getRequestHeaders());
        headers.put("Authorization", "Bearer " + token);
        if (StringUtils.isNotBlank(context.getApplicationId())) headers.put("X-Aether-Application-Id", context.getApplicationId());
        if (StringUtils.isNotBlank(context.getProductProfileId())) headers.put("X-Aether-Product-Profile-Id", context.getProductProfileId());
        if ("send_email".equals(toolName)) {
            String credentialRef = "user-default";
            Map<String, String> credential = runtimeEmailCredentialStore == null ? null
                    : runtimeEmailCredentialStore.get(context.getRunId(), context.getUserId(), credentialRef);
            if (credential != null) {
                credential = new LinkedHashMap<>(credential);
            }
            AgentDefinition agent = agentDefinitionService == null ? null : agentDefinitionService.getById(context.getAgentDefinitionId());
            if (agent == null || !Boolean.TRUE.equals(agent.getSmtpEnabled())) {
                throw new ServerException(422, "当前 Agent 未启用邮件发送");
            }
            if (hasEmailConfiguration(agent)) {
                credential = buildAgentEmailCredential(agent, credential);
            }
            if (userService != null) {
                User user = userService.getById(context.getUserId());
                if (!hasEmailCredential(credential) && user != null && StringUtils.isNoneBlank(user.getEmail(), user.getSmtpAuthorizationCode(), user.getSmtpHost(), user.getSmtpSecurity())
                        && user.getSmtpPort() != null) {
                    if (credential == null) credential = new LinkedHashMap<>();
                    credential.putIfAbsent("sender_email", user.getEmail());
                    credential.putIfAbsent("smtp_authorization_code", AesUtil.decrypt(user.getSmtpAuthorizationCode()));
                    credential.put("smtp_host", user.getSmtpHost());
                    credential.put("smtp_port", String.valueOf(user.getSmtpPort()));
                    credential.put("security", user.getSmtpSecurity());
                }
            }
            if (credential == null || emailCredentialTokenService == null) {
                throw new ServerException(422, "邮件临时凭据不存在或已过期");
            }
            Map<String, String> tokens = new LinkedHashMap<>();
            tokens.put(credentialRef, emailCredentialTokenService.create(context.getRunId(), context.getUserId(), credentialRef, credential));
            headers.put("X-Aether-Email-Credentials", JSON.toJSONString(tokens));
        }
        if (StringUtils.isNotBlank(context.getIdempotencyKey())) {
            headers.put("X-Aether-Idempotency-Key", context.getIdempotencyKey());
        }
        // 仅修改当前内存对象，绝不写回数据库或复用为长期静态令牌。
        server.setRequestHeaders(headers.toJSONString());
        return token;
    }

    /**
     * SMTP 连接信息由服务端凭据令牌承载；兼容历史工作流时也不能将这些字段转发给 MCP。
     */
    private void sanitizeEmailArguments(String toolName, Map<String, Object> arguments) {
        if (!"send_email".equals(toolName)) return;
        arguments.remove("credential_ref");
        arguments.remove("smtp_host");
        arguments.remove("smtp_port");
        arguments.remove("security");
    }

    /**
     * Model-generated identity arguments never authorize a tool call. For a
     * declared trusted-context key, the server value replaces the model value;
     * keys not requested by a tool are intentionally not appended so schemas
     * remain stable for unrelated tools.
     */
    private Map<String, Object> applyTrustedContext(ToolExecutionContext context, Map<String, Object> arguments) {
        if (StringUtils.isBlank(context.getTrustedContext())) return Collections.emptyMap();
        try {
            String stored = context.getTrustedContext();
            String json = stored.startsWith("v1:") ? AesUtil.decrypt(stored.substring(3)) : stored;
            JSONObject trusted = JSON.parseObject(json);
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : trusted.keySet()) {
                Object value = trusted.get(key);
                result.put(key, value);
                if (arguments.containsKey(key)) arguments.put(key, value);
            }
            return result;
        } catch (RuntimeException ex) {
            throw new ServerException(422, "TRUSTED_CONTEXT_INVALID");
        }
    }

    private boolean hasEmailConfiguration(AgentDefinition agent) {
        return agent != null && StringUtils.isNoneBlank(agent.getSmtpSenderEmail(), agent.getSmtpAuthorizationCode(),
                agent.getSmtpHost(), agent.getSmtpSecurity()) && agent.getSmtpPort() != null;
    }

    private boolean hasEmailCredential(Map<String, String> credential) {
        return credential != null && StringUtils.isNoneBlank(credential.get("sender_email"), credential.get("smtp_authorization_code"),
                credential.get("smtp_host"), credential.get("smtp_port"), credential.get("security"));
    }

    private Map<String, String> buildAgentEmailCredential(AgentDefinition agent, Map<String, String> runtimeCredential) {
        Map<String, String> credential = runtimeCredential == null ? new LinkedHashMap<>() : runtimeCredential;
        credential.put("sender_email", agent.getSmtpSenderEmail());
        credential.put("smtp_authorization_code", AesUtil.decrypt(agent.getSmtpAuthorizationCode()));
        credential.put("smtp_host", agent.getSmtpHost());
        credential.put("smtp_port", String.valueOf(agent.getSmtpPort()));
        credential.put("security", agent.getSmtpSecurity());
        return credential;
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
                if ("authorization".equalsIgnoreCase(key) || "x-aether-email-credentials".equalsIgnoreCase(key)) {
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
