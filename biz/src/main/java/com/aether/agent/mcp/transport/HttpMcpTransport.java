package com.aether.agent.mcp.transport;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.mcp.McpResponse;
import com.aether.agent.mcp.McpSession;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP-based MCP transport.
 */
@Component
public class HttpMcpTransport implements McpTransport {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 处理supports。
     */
    @Override
    public boolean supports(String transport) {
        return StringUtils.isBlank(transport)
                || "http".equalsIgnoreCase(transport)
                || "streamable_http".equalsIgnoreCase(transport);
    }

    /**
     * 发送当前请求。
     */
    @Override
    public McpResponse send(AgentMcpServer server, McpSession session, JSONObject body) {
        return send(server, session, body, null);
    }

    @Override
    public McpResponse send(AgentMcpServer server, McpSession session, JSONObject body,
                            String connectorCredentialToken) {
        RestTemplate restTemplate = createRestTemplate(server);
        HttpHeaders headers = createHeaders(server, session, connectorCredentialToken);
        byte[] requestBody = body.toJSONString().getBytes(StandardCharsets.UTF_8);
        HttpEntity<byte[]> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(server.getBaseUrl(), HttpMethod.POST, entity, byte[].class);

        McpResponse mcpResponse = new McpResponse();
        byte[] responseBody = response.getBody();
        mcpResponse.setBody(responseBody == null ? null : new String(responseBody, StandardCharsets.UTF_8));
        mcpResponse.setStatusCode(response.getStatusCodeValue());
        mcpResponse.setSessionId(response.getHeaders().getFirst("Mcp-Session-Id"));
        return mcpResponse;
    }

    /**
     * 创建RestTemplate。
     */
    private RestTemplate createRestTemplate(AgentMcpServer server) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = server.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : server.getTimeoutMs();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return new RestTemplate(requestFactory);
    }

    /**
     * 创建Headers。
     */
    private HttpHeaders createHeaders(AgentMcpServer server, McpSession session,
                                      String connectorCredentialToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.add("Accept", "application/json, text/event-stream");
        if (session != null && StringUtils.isNotBlank(session.getSessionId())) {
            headers.add("Mcp-Session-Id", session.getSessionId());
        }
        applyCustomHeaders(headers, server.getRequestHeaders());
        applyAuth(headers, server);
        if (StringUtils.isNotBlank(connectorCredentialToken)) {
            headers.set("X-Aether-Connector-Credential", connectorCredentialToken);
        }
        applyTraceContext(headers);
        return headers;
    }

    /** 将当前请求的低基数 Trace ID 以 W3C 格式传播到 MCP；不携带凭据或业务内容。 */
    private void applyTraceContext(HttpHeaders headers) {
        String traceId = MDC.get("traceId");
        if (StringUtils.isBlank(traceId)) return;
        String normalized = traceId.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        if (normalized.length() < 32) normalized = String.format("%032x", normalized.hashCode());
        if (normalized.length() > 32) normalized = normalized.substring(0, 32);
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        headers.set("traceparent", "00-" + normalized + "-" + spanId + "-01");
    }

    /**
     * 处理applyCustomHeaders。
     */
    private void applyCustomHeaders(HttpHeaders headers, String headersJson) {
        if (StringUtils.isBlank(headersJson)) {
            return;
        }
        JSONObject object = JSON.parseObject(headersJson);
        for (String key : object.keySet()) {
            if (StringUtils.isNotBlank(key) && object.get(key) != null) {
                headers.set(key, String.valueOf(object.get(key)));
            }
        }
    }

    /**
     * 处理applyAuth。
     */
    private void applyAuth(HttpHeaders headers, AgentMcpServer server) {
        if (StringUtils.isBlank(server.getAuthType()) || "none".equalsIgnoreCase(server.getAuthType())
                || StringUtils.isBlank(server.getAuthToken())) {
            return;
        }
        String token = AesUtil.decrypt(server.getAuthToken());
        if ("bearer".equalsIgnoreCase(server.getAuthType())) {
            headers.setBearerAuth(token);
        } else if ("api_key".equalsIgnoreCase(server.getAuthType())) {
            headers.set("X-API-Key", token);
        }
    }
}
