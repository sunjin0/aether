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
import org.springframework.beans.factory.annotation.Autowired;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI兼容模型客户端。
 */
@Component
public class OpenAIModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIModelClient.class);
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final PooledHttpClient pooledHttpClient;

    @Autowired
    public OpenAIModelClient(PooledHttpClient pooledHttpClient) {
        this.pooledHttpClient = pooledHttpClient;
    }

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
            JSONObject body = createBody(request, false);
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
        try {
            JSONObject body = createBody(request, true);
            String url = buildChatUrl(provider.getApiBaseUrl());
            String authorization = StringUtils.isNotBlank(provider.getApiKey())
                    ? "Bearer " + AesUtil.decrypt(provider.getApiKey()) : null;

            long t0 = System.currentTimeMillis();
            try (PooledHttpClient.HttpStreamResult result = pooledHttpClient.postStream(url, body.toJSONString(), authorization)) {
                long t1 = System.currentTimeMillis();
                log.info("模型连接耗时: {}ms, provider={}, model={}", t1 - t0, provider.getName(), agent.getModel());
                return parseStream(result.getInputStream(), agent.getModel(), callback);
            }
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            log.error("模型流式调用异常, provider={}", provider.getName(), e);
            throw new ServerException(500, "模型调用失败");
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

    private JSONObject createBody(ModelChatRequest request, boolean stream) {
        AgentDefinition agent = request.getAgent();
        List<ModelChatMessage> messages = request.getMessages();
        List<AgentTool> tools = request.getTools();

        JSONObject body = new JSONObject();
        body.put("model", StringUtils.defaultIfBlank(request.getModel(), StringUtils.defaultIfBlank(agent.getModel(), "gpt-3.5-turbo")));
        body.put("messages", toJsonMessages(messages));

        // 温度参数：优先使用请求中的值，否则使用Agent配置
        BigDecimal temperature = request.getTemperature() != null ? request.getTemperature() : agent.getTemperature();
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        // 最大token数：优先使用请求中的值，否则使用Agent配置
        Integer maxCompletionTokens = request.getMaxCompletionTokens() != null ? request.getMaxCompletionTokens() : request.getMaxTokens();
        if (maxCompletionTokens == null) {
            maxCompletionTokens = agent.getMaxTokens();
        }
        if (maxCompletionTokens != null) {
            body.put("max_completion_tokens", maxCompletionTokens);
        }

        body.put("stream", stream);

        // 流式响应选项
        if (stream && request.getStreamOptions() != null) {
            body.put("stream_options", request.getStreamOptions());
        } else if (stream) {
            // 默认启用usage统计
            JSONObject streamOptions = new JSONObject();
            streamOptions.put("include_usage", true);
            body.put("stream_options", streamOptions);
        }

        // 核采样参数
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }

        // 频率惩罚
        if (request.getFrequencyPenalty() != null) {
            body.put("frequency_penalty", request.getFrequencyPenalty());
        }

        // 存在惩罚
        if (request.getPresencePenalty() != null) {
            body.put("presence_penalty", request.getPresencePenalty());
        }

        // 停止序列
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            body.put("stop", request.getStop());
        }

        // 响应格式
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat());
        }

        // 可重复性种子
        if (request.getSeed() != null) {
            body.put("seed", request.getSeed());
        }

        // 用户标识
        if (StringUtils.isNotBlank(request.getUser())) {
            body.put("user", request.getUser());
        }

        // 推理力度：优先使用请求中的值，否则使用Agent配置
        String reasoningEffort = StringUtils.defaultIfBlank(request.getReasoningEffort(),
                Boolean.TRUE.equals(agent.getDefaultThinking()) ? agent.getDefaultReasoningEffort() : null);
        if (StringUtils.isNotBlank(reasoningEffort)) {
            body.put("reasoning_effort", reasoningEffort);
        }

        // 对数概率
        if (Boolean.TRUE.equals(request.getLogprobs())) {
            body.put("logprobs", true);
            if (request.getTopLogprobs() != null) {
                body.put("top_logprobs", request.getTopLogprobs());
            }
        }

        // token概率偏移
        if (request.getLogitBias() != null && !request.getLogitBias().isEmpty()) {
            JSONObject logitBiasJson = new JSONObject();
            request.getLogitBias().forEach((tokenId, score) -> logitBiasJson.put(String.valueOf(tokenId), score));
            body.put("logit_bias", logitBiasJson);
        }

        // 工具配置
        JSONArray toolArray = toJsonTools(tools);
        if (!toolArray.isEmpty()) {
            body.put("tools", toolArray);
            if (StringUtils.isNotBlank(request.getToolChoiceName())) {
                JSONObject toolChoiceObj = new JSONObject();
                toolChoiceObj.put("type", "function");
                toolChoiceObj.put("function", new JSONObject().fluentPut("name", request.getToolChoiceName()));
                body.put("tool_choice", toolChoiceObj);
            } else if (StringUtils.isNotBlank(request.getToolChoice())) {
                body.put("tool_choice", request.getToolChoice());
            } else {
                body.put("tool_choice", "auto");
            }
        }

        return body;
    }

    private JSONArray toJsonMessages(List<ModelChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages == null) {
            return array;
        }
        for (ModelChatMessage message : messages) {
            String role = message.getRole();
            // assistant 有 tool_calls 时 content 可省略，其他角色必须有 content
            boolean hasToolCalls = StringUtils.isNotBlank(message.getToolCalls());
            
            log.debug("toJsonMessages: role={}, content={}, toolCallId={}, hasToolCalls={}", 
                    role, message.getContent(), message.getToolCallId(), hasToolCalls);
            
            if ("assistant".equals(role) && hasToolCalls) {
                JSONObject item = new JSONObject();
                item.put("role", role);
                item.put("tool_calls", JSONArray.parseArray(message.getToolCalls()));
                array.add(item);
            } else {
                JSONObject item = new JSONObject();
                item.put("role", role);
                item.put("content", StringUtils.defaultString(message.getContent(), ""));
                if (hasToolCalls) {
                    item.put("tool_calls", JSONArray.parseArray(message.getToolCalls()));
                }
                if (StringUtils.isNotBlank(message.getToolCallId())) {
                    item.put("tool_call_id", message.getToolCallId());
                }
                array.add(item);
            }
        }
        return array;
    }

    private static final java.util.regex.Pattern PLACEHOLDER_PATTERN = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");

    private JSONArray toJsonTools(List<AgentTool> tools) {
        JSONArray array = new JSONArray();
        if (tools == null || tools.isEmpty()) {
            return array;
        }
        for (AgentTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getCode())) {
                continue;
            }
            if (StringUtils.isNotBlank(tool.getParametersSchema())) {
                array.add(toInternalTool(tool));
                continue;
            }
            
            // 从模板中提取参数
            JSONObject properties = new JSONObject();
            java.util.Set<String> required = new java.util.LinkedHashSet<>();
            
            // 从 httpBodyTemplate 提取参数
            extractPlaceholders(tool.getHttpBodyTemplate(), properties, required);
            // 从 httpUrl 提取参数
            extractPlaceholders(tool.getHttpUrl(), properties, required);
            // 从 httpHeaders 提取参数
            extractPlaceholders(tool.getHttpHeaders(), properties, required);
            
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", properties);
            if (!required.isEmpty()) {
                parameters.put("required", new JSONArray(required));
            }
            parameters.put("additionalProperties", true);

            JSONObject function = new JSONObject();
            function.put("name", tool.getName());
            function.put("description", StringUtils.defaultIfBlank(tool.getDescription(), tool.getCode()));
            function.put("parameters", parameters);

            JSONObject item = new JSONObject();
            item.put("type", "function");
            item.put("function", function);
            array.add(item);
        }
        return array;
    }

    private JSONObject toInternalTool(AgentTool tool) {
        JSONObject function = new JSONObject();
        function.put("name", tool.getName());
        function.put("description", tool.getDescription());
        function.put("parameters", JSONObject.parseObject(tool.getParametersSchema()));
        return new JSONObject().fluentPut("type", "function").fluentPut("function", function);
    }

    private void extractPlaceholders(String template, JSONObject properties, java.util.Set<String> required) {
        if (StringUtils.isBlank(template)) {
            return;
        }
        java.util.regex.Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (!properties.containsKey(paramName)) {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                prop.put("description", "参数: " + paramName);
                properties.put(paramName, prop);
                required.add(paramName);
            }
        }
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

        // 用于累积流式工具调用（按index分组）
        Map<Integer, JSONObject> toolCallsMap = new LinkedHashMap<>();

        long parseStart = System.currentTimeMillis();
        long firstTokenTime = -1;
        int chunkCount = 0;

        // 使用小缓冲区避免SSE数据被缓冲（默认8KB会攒很多chunk才返回）
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[1];
            StringBuilder lineBuffer = new StringBuilder();
            while (reader.read(buffer) != -1) {
                if (callback != null && callback.isClosed()) {
                    break;
                }
                char c = buffer[0];
                if (c == '\n') {
                    String line = lineBuffer.toString();
                    lineBuffer.setLength(0);
                    
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
                    chunkCount++;
                    if (firstTokenTime == -1) {
                        firstTokenTime = System.currentTimeMillis();
                        log.info("首token耗时: {}ms", firstTokenTime - parseStart);
                    }
                    parseStreamData(data, defaultModel, callback, content, reasoningContent, response, toolCallsMap);
                } else if (c != '\r') {
                    lineBuffer.append(c);
                }
            }
            // 处理最后一行（如果没有换行符结尾）
            if (lineBuffer.length() > 0) {
                String line = lineBuffer.toString();
                if (line.startsWith("data:")) {
                    String data = line.substring("data:".length()).trim();
                    if (StringUtils.isNotBlank(data) && !"[DONE]".equals(data)) {
                        parseStreamData(data, defaultModel, callback, content, reasoningContent, response, toolCallsMap);
                    }
                }
            }
        }

        long totalMs = System.currentTimeMillis() - parseStart;
        log.info("流式完成: 总耗时={}ms, chunks={}, 首token={}ms, 内容长度={}",
                totalMs, chunkCount,
                firstTokenTime != -1 ? firstTokenTime - parseStart : "N/A",
                content.length());

        response.setContent(content.toString());
        response.setReasoningContent(reasoningContent.toString());
        response.setRawResponse(raw.toString());


        // 将累积的工具调用转换为JSON字符串
        if (!toolCallsMap.isEmpty()) {
            JSONArray toolCallsArray = new JSONArray();
            toolCallsArray.addAll(toolCallsMap.values());
            response.setToolCalls(toolCallsArray.toJSONString());
        }

        return response;
    }

    private void parseStreamData(String data, String defaultModel, ModelStreamCallback callback,
                                 StringBuilder content, StringBuilder reasoningContent,
                                 ModelStreamResponse response, Map<Integer, JSONObject> toolCallsMap) {
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
        if (toolCalls != null && !toolCalls.isEmpty()) {
            // 累积工具调用（流式时tool_calls按index分片到达）
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCallChunk = toolCalls.getJSONObject(i);
                int index = toolCallChunk.getIntValue("index");
                JSONObject existing = toolCallsMap.get(index);
                if (existing == null) {
                    // 首次出现，直接存入
                    toolCallsMap.put(index, toolCallChunk);
                } else {
                    // 后续分片，合并function.arguments
                    JSONObject existingFunction = existing.getJSONObject("function");
                    JSONObject chunkFunction = toolCallChunk.getJSONObject("function");
                    if (existingFunction != null && chunkFunction != null) {
                        String existingArgs = existingFunction.getString("arguments");
                        String chunkArgs = chunkFunction.getString("arguments");
                        if (StringUtils.isNotBlank(chunkArgs)) {
                            existingFunction.put("arguments", StringUtils.defaultString(existingArgs) + chunkArgs);
                        }
                        // 合并name（通常只在第一个分片）
                        if (StringUtils.isBlank(existingFunction.getString("name")) && StringUtils.isNotBlank(chunkFunction.getString("name"))) {
                            existingFunction.put("name", chunkFunction.getString("name"));
                        }
                    }
                }
            }
            if (callback != null) {
                callback.onToolCall(toolCalls.toJSONString());
            }
        }
    }
}
