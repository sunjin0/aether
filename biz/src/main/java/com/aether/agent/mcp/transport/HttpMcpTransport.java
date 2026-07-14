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

import java.nio.charset.StandardCharsets;

/**
 * HTTP-based MCP transport.
 */
@Component
public class HttpMcpTransport implements McpTransport {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    @Override
    public boolean supports(String transport) {
        return StringUtils.isBlank(transport)
                || "http".equalsIgnoreCase(transport)
                || "sse".equalsIgnoreCase(transport)
                || "streamable_http".equalsIgnoreCase(transport);
    }

    @Override
    public McpResponse send(AgentMcpServer server, McpSession session, JSONObject body) {
        RestTemplate restTemplate = createRestTemplate(server);
        HttpHeaders headers = createHeaders(server, session);
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

    private RestTemplate createRestTemplate(AgentMcpServer server) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = server.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : server.getTimeoutMs();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return new RestTemplate(requestFactory);
    }

    private HttpHeaders createHeaders(AgentMcpServer server, McpSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.add("Accept", "application/json, text/event-stream");
        if (session != null && StringUtils.isNotBlank(session.getSessionId())) {
            headers.add("Mcp-Session-Id", session.getSessionId());
        }
        applyCustomHeaders(headers, server.getRequestHeaders());
        applyAuth(headers, server);
        return headers;
    }

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
