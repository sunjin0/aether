package com.aether.agent.model;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.exception.ServerException;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI兼容模型客户端。
 */
@Component
public class OpenAIModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIModelClient.class);
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final int STREAM_READ_TIMEOUT_MS = 300000; // 流式读超时5分钟，推理模型需要更长响应时间

    @Override
    public boolean supports(String providerType) {
        return "openai".equalsIgnoreCase(providerType) || "local".equalsIgnoreCase(providerType);
    }

    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        ModelProvider provider = request.getProvider();
        AgentDefinition agent = request.getAgent();
        try {
            RestTemplate restTemplate = createRestTemplate();
            HttpHeaders headers = createHeaders(provider);
            JSONObject body = createBody(agent, request.getMessages(), request.getTools());
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    buildChatUrl(provider.getApiBaseUrl()),
                    HttpMethod.POST,
                    entity,
                    String.class);
            return parseResponse(response.getBody(), agent.getModel());
        } catch (ResourceAccessException e) {
            throw new ServerException(503, "模型供应商调用超时");
        } catch (ServerException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ServerException(500, "模型调用失败");
        } catch (Exception e) {
            throw new ServerException(500, "模型响应解析失败");
        }
    }

    @Override
    public ModelStreamResponse stream(ModelChatRequest request, ModelStreamCallback callback) {
        ModelProvider provider = request.getProvider();
        AgentDefinition agent = request.getAgent();
        HttpURLConnection connection = null;
        try {
            JSONObject body = createBody(agent, request.getMessages(), request.getTools(), true);
            URL url = new URL(buildChatUrl(provider.getApiBaseUrl()));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
            connection.setReadTimeout(STREAM_READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            if (StringUtils.isNotBlank(provider.getApiKey())) {
                connection.setRequestProperty("Authorization", "Bearer " + AesUtil.decrypt(provider.getApiKey()));
            }

            byte[] payload = body.toJSONString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                log.error("模型调用失败, status={}, provider={}", status, provider.getName());
                throw new ServerException(500, "模型调用失败");
            }
            return parseStream(connection.getInputStream(), agent.getModel(), callback);
        } catch (SocketTimeoutException e) {
            log.error("模型流式调用超时, provider={}, readTimeout={}ms", provider.getName(), STREAM_READ_TIMEOUT_MS, e);
            throw new ServerException(503, "模型供应商调用超时");
        } catch (ServerException e) {
            throw e;
        } catch (IOException e) {
            log.error("模型流式调用IO异常, provider={}", provider.getName(), e);
            throw new ServerException(503, "模型供应商调用超时");
        } catch (Exception e) {
            log.error("模型流式调用异常, provider={}", provider.getName(), e);
            throw new ServerException(500, "模型调用失败");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        requestFactory.setReadTimeout(DEFAULT_TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    private HttpHeaders createHeaders(ModelProvider provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
        }
        return headers;
    }

    private JSONObject createBody(AgentDefinition agent, List<ModelChatMessage> messages) {
        return createBody(agent, messages, null, false);
    }

    private JSONObject createBody(AgentDefinition agent, List<ModelChatMessage> messages, List<AgentTool> tools) {
        return createBody(agent, messages, tools, false);
    }

    private JSONObject createBody(AgentDefinition agent, List<ModelChatMessage> messages, List<AgentTool> tools, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", StringUtils.defaultIfBlank(agent.getModel(), "gpt-3.5-turbo"));
        body.put("messages", toJsonMessages(messages));
        body.put("temperature", agent.getTemperature());
        body.put("max_tokens", agent.getMaxTokens());
        body.put("stream", stream);

        // 深度思考配置
        if (Boolean.TRUE.equals(agent.getDefaultThinking())) {
            String effort = StringUtils.defaultIfBlank(agent.getDefaultReasoningEffort(), "medium");
            body.put("reasoning_effort", effort);
        }

        JSONArray toolArray = toJsonTools(tools);
        if (!toolArray.isEmpty()) {
            body.put("tools", toolArray);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private JSONArray toJsonMessages(List<ModelChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages == null) {
            return array;
        }
        for (ModelChatMessage message : messages) {
            JSONObject item = new JSONObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            if (StringUtils.isNotBlank(message.getToolCalls())) {
                item.put("tool_calls", JSONArray.parseArray(message.getToolCalls()));
            }
            if (StringUtils.isNotBlank(message.getToolCallId())) {
                item.put("tool_call_id", message.getToolCallId());
            }
            array.add(item);
        }
        return array;
    }

    private JSONArray toJsonTools(List<AgentTool> tools) {
        JSONArray array = new JSONArray();
        if (tools == null || tools.isEmpty()) {
            return array;
        }
        for (AgentTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getCode())) {
                continue;
            }
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("additionalProperties", true);

            JSONObject function = new JSONObject();
            function.put("name", tool.getCode());
            function.put("description", StringUtils.defaultIfBlank(tool.getDescription(), tool.getName()));
            function.put("parameters", parameters);

            JSONObject item = new JSONObject();
            item.put("type", "function");
            item.put("function", function);
            array.add(item);
        }
        return array;
    }

    private String buildChatUrl(String apiBaseUrl) {
        if (StringUtils.isBlank(apiBaseUrl)) {
            throw new ServerException(422, "模型供应商API地址为空");
        }
        String baseUrl = StringUtils.removeEnd(apiBaseUrl, "/");
        if (baseUrl.endsWith("/v1/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/v1/chat/completions";
    }

    private ModelChatResponse parseResponse(String responseBody, String defaultModel) {
        if (StringUtils.isBlank(responseBody)) {
            throw new ServerException(500, "模型响应为空");
        }
        JSONObject json = JSONObject.parseObject(responseBody);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ServerException(500, "模型响应缺少choices");
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        String reasoningContent = message == null ? null : message.getString("reasoning_content");
        JSONArray toolCalls = message == null ? null : message.getJSONArray("tool_calls");
        if (StringUtils.isBlank(content) && StringUtils.isBlank(reasoningContent) && (toolCalls == null || toolCalls.isEmpty())) {
            throw new ServerException(500, "模型响应内容为空");
        }

        JSONObject usage = json.getJSONObject("usage");
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(StringUtils.defaultString(content));
        response.setReasoningContent(reasoningContent);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            response.setToolCalls(toolCalls.toJSONString());
        }
        response.setModel(StringUtils.defaultIfBlank(json.getString("model"), defaultModel));
        if (usage != null) {
            response.setPromptTokens(usage.getInteger("prompt_tokens"));
            response.setCompletionTokens(usage.getInteger("completion_tokens"));
            response.setTotalTokens(usage.getInteger("total_tokens"));
            JSONObject completionTokensDetails = usage.getJSONObject("completion_tokens_details");
            if (completionTokensDetails != null) {
                response.setReasoningTokens(completionTokensDetails.getInteger("reasoning_tokens"));
            }
        }
        response.setRawResponse(responseBody);
        return response;
    }

    private ModelStreamResponse parseStream(InputStream inputStream, String defaultModel, ModelStreamCallback callback) throws IOException {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        ModelStreamResponse response = new ModelStreamResponse();
        response.setModel(defaultModel);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (callback != null && callback.isClosed()) {
                    break;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (StringUtils.isBlank(data)) {
                    continue;
                }
                raw.append(data).append('\n');
                if ("[DONE]".equals(data)) {
                    break;
                }
                parseStreamData(data, defaultModel, callback, content, reasoningContent, response);
            }
        }

        response.setContent(content.toString());
        response.setReasoningContent(reasoningContent.toString());
        response.setRawResponse(raw.toString());
        return response;
    }

    private void parseStreamData(String data, String defaultModel, ModelStreamCallback callback,
                                 StringBuilder content, StringBuilder reasoningContent,
                                 ModelStreamResponse response) {
        JSONObject json = JSONObject.parseObject(data);
        response.setModel(StringUtils.defaultIfBlank(json.getString("model"), defaultModel));
        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            response.setPromptTokens(usage.getInteger("prompt_tokens"));
            response.setCompletionTokens(usage.getInteger("completion_tokens"));
            response.setTotalTokens(usage.getInteger("total_tokens"));
            JSONObject completionTokensDetails = usage.getJSONObject("completion_tokens_details");
            if (completionTokensDetails != null) {
                response.setReasoningTokens(completionTokensDetails.getInteger("reasoning_tokens"));
            }
        }

        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return;
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject delta = firstChoice.getJSONObject("delta");
        if (delta == null) {
            return;
        }
        String chunk = delta.getString("content");
        if (StringUtils.isNotEmpty(chunk)) {
            content.append(chunk);
            if (callback != null) {
                callback.onMessage(chunk);
            }
        }
        String reasoningChunk = delta.getString("reasoning_content");
        if (StringUtils.isNotEmpty(reasoningChunk)) {
            reasoningContent.append(reasoningChunk);
            if (callback != null) {
                callback.onReasoning(reasoningChunk);
            }
        }
        JSONArray toolCalls = delta.getJSONArray("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty() && callback != null) {
            callback.onToolCall(toolCalls.toJSONString());
        }
    }
}
