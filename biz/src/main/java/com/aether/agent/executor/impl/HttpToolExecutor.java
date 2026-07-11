package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.extractor.ResponseExtractor;
import com.aether.agent.security.ToolSecurityValidator;
import com.aether.agent.template.TemplateRenderer;
import com.aether.exception.ServerException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 工具执行器。
 */
@Component
public class HttpToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpToolExecutor.class);
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final int MAX_RESPONSE_BODY = 65536; // 64KB

    private final ToolSecurityValidator securityValidator;
    private final TemplateRenderer templateRenderer;
    private final ResponseExtractor responseExtractor;

    public HttpToolExecutor(ToolSecurityValidator securityValidator,
                            TemplateRenderer templateRenderer,
                            ResponseExtractor responseExtractor) {
        this.securityValidator = securityValidator;
        this.templateRenderer = templateRenderer;
        this.responseExtractor = responseExtractor;
    }

    @Override
    public boolean supports(String toolType) {
        return "http".equalsIgnoreCase(toolType);
    }

    private String renderUrl(AgentTool tool, Map<String, Object> arguments) {
        String template = tool.getHttpUrl();
        String renderedUrl = templateRenderer.render(template, arguments);
        if (!"GET".equalsIgnoreCase(tool.getHttpMethod()) || StringUtils.isBlank(renderedUrl)
                || StringUtils.contains(template, "${") || arguments == null || arguments.isEmpty()) {
            return renderedUrl;
        }

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(String.valueOf(entry.getValue())));
        }
        if (query.length() == 0) {
            return renderedUrl;
        }
        return renderedUrl + (renderedUrl.contains("?") ? "&" : "?") + query;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        AgentTool tool = context.getTool();
        long startTime = System.currentTimeMillis();

        // 先渲染请求信息，确保失败时也能保存
        String url = null;
        String method = tool.getHttpMethod();
        Map<String, String> headers = null;
        String body = null;

        try {
            // 1. 校验方法
            securityValidator.validateMethod(method);

            // 2. 渲染请求头和请求体
            url = renderUrl(tool, context.getArguments());
            headers = renderHeaders(tool.getHttpHeaders(), context.getArguments());
            body = renderBody(tool, context.getArguments());
            if (StringUtils.isNotBlank(body) && !containsHeader(headers, "Content-Type")) {
                headers.put("Content-Type", "application/json; charset=UTF-8");
            }

            // 3. 校验URL（可能抛出异常）
            securityValidator.validateUrl(url);

            // 4. 校验请求头
            securityValidator.validateHeaders(headers.keySet());

            // 5. 执行HTTP请求
            ToolExecutionResult result = executeHttpRequest(url, method, headers, body, tool, startTime);
            result.setRequestUrl(url);
            result.setRequestMethod(method);
            result.setRequestHeaders(JSON.toJSONString(headers));
            result.setRequestBody(body);
            return result;

        } catch (ServerException e) {
            log.error("工具执行安全拦截: tool={}, error={}", tool.getCode(), e.getMessage());
            ToolExecutionResult result = ToolExecutionResult.failure(e.getMessage(), 3);
            result.setRequestUrl(url != null ? url : tool.getHttpUrl());
            result.setRequestMethod(method);
            result.setRequestHeaders(headers != null ? JSON.toJSONString(headers) : null);
            result.setRequestBody(body);
            return result;
        } catch (SocketTimeoutException e) {
            log.error("工具执行超时: tool={}", tool.getCode());
            ToolExecutionResult result = ToolExecutionResult.failure("工具执行超时", 2);
            result.setRequestUrl(url != null ? url : tool.getHttpUrl());
            result.setRequestMethod(method);
            result.setRequestHeaders(headers != null ? JSON.toJSONString(headers) : null);
            result.setRequestBody(body);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: tool={}", tool.getCode(), e);
            ToolExecutionResult result = ToolExecutionResult.failure("工具执行失败: " + e.getMessage(), 1);
            result.setRequestUrl(url != null ? url : tool.getHttpUrl());
            result.setRequestMethod(method);
            result.setRequestHeaders(headers != null ? JSON.toJSONString(headers) : null);
            result.setRequestBody(body);
            return result;
        }
    }

    /**
     * 执行HTTP请求
     */
    private ToolExecutionResult executeHttpRequest(String url, String method,
                                                    Map<String, String> headers, String body,
                                                    AgentTool tool, long startTime) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL targetUrl = new URL(url);
            connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod(method.toUpperCase());
            
            int timeout = tool.getTimeoutMs() != null ? tool.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setDoOutput("POST".equalsIgnoreCase(method));

            // 设置请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // 发送请求体
            if ("POST".equalsIgnoreCase(method) && StringUtils.isNotBlank(body)) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(payload);
                }
            }

            // 获取响应
            int status = connection.getResponseCode();
            String responseBody = readResponseBody(connection, status);

            // 校验响应大小
            securityValidator.validateResponseSize(responseBody.length());

            long latencyMs = System.currentTimeMillis() - startTime;

            // 提取响应内容
            String extractedContent = responseExtractor.extract(responseBody, tool.getResponseExtractRule());

            return ToolExecutionResult.success(
                    truncate(extractedContent, 4096),
                    truncate(responseBody, MAX_RESPONSE_BODY),
                    status,
                    (int) latencyMs
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 读取响应体
     */
    private String readResponseBody(HttpURLConnection connection, int status) throws IOException {
        InputStream inputStream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (inputStream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    /**
     * 渲染请求头
     */
    private Map<String, String> renderHeaders(String headersJson, Map<String, Object> arguments) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        if (StringUtils.isBlank(headersJson)) {
            return headers;
        }

        try {
            JSONObject jsonHeaders = JSON.parseObject(headersJson);
            for (String key : jsonHeaders.keySet()) {
                String value = jsonHeaders.getString(key);
                headers.put(key, templateRenderer.render(value, arguments));
            }
        } catch (Exception e) {
            log.warn("解析请求头模板失败: {}", headersJson, e);
        }

        return headers;
    }

    /**
     * 渲染请求体
     */
    private String renderBody(AgentTool tool, Map<String, Object> arguments) {
        if (StringUtils.isBlank(tool.getHttpBodyTemplate())) {
            if ("POST".equalsIgnoreCase(tool.getHttpMethod()) && arguments != null && !arguments.isEmpty()) {
                return JSON.toJSONString(arguments);
            }
            return null;
        }

        try {
            return templateRenderer.renderJson(tool.getHttpBodyTemplate(), arguments);
        } catch (Exception e) {
            log.warn("渲染请求体模板失败: {}", tool.getHttpBodyTemplate(), e);
            return tool.getHttpBodyTemplate();
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean containsHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        for (String header : headers.keySet()) {
            if (name.equalsIgnoreCase(header)) {
                return true;
            }
        }
        return false;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
}
