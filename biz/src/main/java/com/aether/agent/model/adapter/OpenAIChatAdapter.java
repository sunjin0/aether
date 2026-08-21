package com.aether.agent.model.adapter;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelStreamCallback;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for OpenAI Chat Completions compatible providers.
 */
@Component
public class OpenAIChatAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAIChatAdapter.class);

    @Override
    public boolean supports(String providerType) {
        return "openai".equalsIgnoreCase(providerType) || "local".equalsIgnoreCase(providerType)
                || "local-openai-compatible".equalsIgnoreCase(providerType);
    }

    @Override
    public String chatUrl(ModelChatRequest request) {
        ModelProvider provider = request.getProvider();
        if (provider == null || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.model.api.base.url.required"));
        }
        String baseUrl = StringUtils.removeEnd(provider.getApiBaseUrl(), "/");
        if (baseUrl.endsWith("/v1/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/v1/chat/completions";
    }

    @Override
    public HttpHeaders headers(ModelChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ModelProvider provider = request.getProvider();
        if (provider != null && StringUtils.isNotBlank(provider.getApiKey())) {
            headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
        }
        return headers;
    }

    @Override
    public Map<String, String> streamHeaders(ModelChatRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        ModelProvider provider = request.getProvider();
        if (provider != null && StringUtils.isNotBlank(provider.getApiKey())) {
            headers.put("Authorization", "Bearer " + AesUtil.decrypt(provider.getApiKey()));
        }
        return headers;
    }

    @Override
    public JSONObject body(ModelChatRequest request, boolean stream) {
        AgentDefinition agent = request.getAgent() != null ? request.getAgent() : new AgentDefinition();
        JSONObject body = new JSONObject();
        body.put("model", resolveModel(request, agent));
        body.put("messages", toJsonMessages(request.getMessages()));
        applyGenerationParameters(body, request, agent, stream);
        applyTools(body, request);
        return body;
    }

    protected String resolveModel(ModelChatRequest request, AgentDefinition agent) {
        return StringUtils.defaultIfBlank(request.getModel(), StringUtils.defaultIfBlank(agent.getModel(), "gpt-3.5-turbo"));
    }

    protected void applyGenerationParameters(JSONObject body, ModelChatRequest request, AgentDefinition agent, boolean stream) {
        BigDecimal temperature = request.getTemperature() != null ? request.getTemperature() : agent.getTemperature();
        if (temperature != null) body.put("temperature", temperature);

        Integer maxCompletionTokens = request.getMaxCompletionTokens() != null ? request.getMaxCompletionTokens() : request.getMaxTokens();
        if (maxCompletionTokens == null) maxCompletionTokens = agent.getMaxTokens();
        if (maxCompletionTokens != null) body.put("max_completion_tokens", maxCompletionTokens);

        body.put("stream", stream);
        if (stream && request.getStreamOptions() != null) {
            body.put("stream_options", request.getStreamOptions());
        } else if (stream) {
            body.put("stream_options", new JSONObject().fluentPut("include_usage", true));
        }

        if (request.getTopP() != null) body.put("top_p", request.getTopP());
        if (request.getFrequencyPenalty() != null) body.put("frequency_penalty", request.getFrequencyPenalty());
        if (request.getPresencePenalty() != null) body.put("presence_penalty", request.getPresencePenalty());
        if (request.getStop() != null && !request.getStop().isEmpty()) body.put("stop", request.getStop());
        if (request.getResponseFormat() != null) body.put("response_format", request.getResponseFormat());
        if (request.getSeed() != null) body.put("seed", request.getSeed());
        if (StringUtils.isNotBlank(request.getUser())) body.put("user", request.getUser());

        String reasoningEffort = StringUtils.defaultIfBlank(request.getReasoningEffort(),
                Boolean.TRUE.equals(agent.getDefaultThinking()) ? agent.getDefaultReasoningEffort() : null);
        if (StringUtils.isNotBlank(reasoningEffort)) body.put("reasoning_effort", reasoningEffort);

        if (Boolean.TRUE.equals(request.getLogprobs())) {
            body.put("logprobs", true);
            if (request.getTopLogprobs() != null) body.put("top_logprobs", request.getTopLogprobs());
        }
        if (request.getLogitBias() != null && !request.getLogitBias().isEmpty()) {
            JSONObject logitBiasJson = new JSONObject();
            request.getLogitBias().forEach((tokenId, score) -> logitBiasJson.put(String.valueOf(tokenId), score));
            body.put("logit_bias", logitBiasJson);
        }
        applyProviderOptions(body, request);
    }

    protected void applyProviderOptions(JSONObject body, ModelChatRequest request) {
        if (request.getProviderOptions() == null || request.getProviderOptions().isEmpty()) {
            return;
        }
        Object extraBody = request.getProviderOptions().get("body");
        if (extraBody instanceof Map) {
            ((Map<?, ?>) extraBody).forEach((key, value) -> {
                if (key != null && value != null) body.put(String.valueOf(key), value);
            });
        }
    }

    protected void applyTools(JSONObject body, ModelChatRequest request) {
        JSONArray toolArray = toJsonTools(request.getTools());
        if (toolArray.isEmpty()) {
            return;
        }
        body.put("tools", toolArray);
        if (StringUtils.isNotBlank(request.getToolChoiceName())) {
            body.put("tool_choice", new JSONObject()
                    .fluentPut("type", "function")
                    .fluentPut("function", new JSONObject().fluentPut("name", request.getToolChoiceName())));
        } else if (StringUtils.isNotBlank(request.getToolChoice())) {
            body.put("tool_choice", request.getToolChoice());
        } else {
            body.put("tool_choice", "auto");
        }
    }

    protected JSONArray toJsonMessages(List<ModelChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages == null) return array;
        for (ModelChatMessage message : messages) {
            String role = message.getRole();
            boolean hasToolCalls = StringUtils.isNotBlank(message.getToolCalls());
            JSONObject item = new JSONObject();
            item.put("role", role);
            item.put("content", StringUtils.defaultString(message.getContent(), ""));
            if ("assistant".equals(role) && StringUtils.isNotBlank(message.getReasoningContent())) {
                item.put("reasoning_content", message.getReasoningContent());
            }
            if (hasToolCalls) {
                item.put("tool_calls", normalizeToolCalls(JSONArray.parseArray(message.getToolCalls())));
            }
            if (StringUtils.isNotBlank(message.getToolCallId())) {
                item.put("tool_call_id", message.getToolCallId());
            }
            array.add(item);
        }
        return array;
    }

    protected JSONArray normalizeToolCalls(JSONArray toolCalls) {
        if (toolCalls == null) return new JSONArray();
        for (int i = 0; i < toolCalls.size(); i++) {
            JSONObject toolCall = toolCalls.getJSONObject(i);
            if (toolCall != null && toolCall.get("id") != null) {
                toolCall.put("id", String.valueOf(toolCall.get("id")));
            }
        }
        return toolCalls;
    }

    protected JSONArray toJsonTools(List<AgentTool> tools) {
        JSONArray array = new JSONArray();
        if (tools == null || tools.isEmpty()) return array;
        for (AgentTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getCode())) continue;
            array.add(StringUtils.isNotBlank(tool.getParametersSchema()) ? toInternalTool(tool) : toMcpTool(tool));
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

    private JSONObject toMcpTool(AgentTool tool) {
        JSONObject function = new JSONObject();
        function.put("name", tool.getName());
        function.put("description", StringUtils.defaultIfBlank(tool.getDescription(), tool.getCode()));
        if (StringUtils.isNotBlank(tool.getMcpInputSchema())) {
            function.put("parameters", JSONObject.parseObject(tool.getMcpInputSchema()));
        } else {
            function.put("parameters", new JSONObject()
                    .fluentPut("type", "object")
                    .fluentPut("properties", new JSONObject())
                    .fluentPut("additionalProperties", true));
        }
        return new JSONObject().fluentPut("type", "function").fluentPut("function", function);
    }

    @Override
    public ModelChatResponse parseResponse(String responseBody, String defaultModel) {
        if (StringUtils.isBlank(responseBody)) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.empty"));
        }
        JSONObject json = parseJson(responseBody);
        if (json.getJSONObject("error") != null) {
            log.error("Model provider returned an error response");
            throw new ServerException(500, I18nUtils.getMessage("agent.model.provider.error"));
        }
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty() || choices.getJSONObject(0) == null) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.invalid"));
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.invalid"));
        }
        String content = message.getString("content");
        String reasoningContent = message.getString("reasoning_content");
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (StringUtils.isBlank(content) && StringUtils.isBlank(reasoningContent) && (toolCalls == null || toolCalls.isEmpty())) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.empty"));
        }
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(StringUtils.defaultString(content));
        response.setReasoningContent(reasoningContent);
        if (toolCalls != null && !toolCalls.isEmpty()) response.setToolCalls(toolCalls.toJSONString());
        response.setModel(StringUtils.defaultIfBlank(json.getString("model"), defaultModel));
        applyUsage(response, json.getJSONObject("usage"));
        response.setRawResponse(responseBody);
        return response;
    }

    protected JSONObject parseJson(String body) {
        try {
            JSONObject json = JSONObject.parseObject(body);
            if (json == null) throw new IllegalArgumentException("empty json");
            return json;
        } catch (Exception e) {
            log.error("Model response is not valid JSON, body={}", body);
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.invalid"));
        }
    }

    protected void applyUsage(ModelChatResponse response, JSONObject usage) {
        if (usage == null) return;
        response.setPromptTokens(usage.getInteger("prompt_tokens"));
        response.setCompletionTokens(usage.getInteger("completion_tokens"));
        response.setTotalTokens(usage.getInteger("total_tokens"));
        JSONObject completionTokensDetails = usage.getJSONObject("completion_tokens_details");
        if (completionTokensDetails != null) {
            response.setReasoningTokens(completionTokensDetails.getInteger("reasoning_tokens"));
        }
    }

    protected void applyUsage(ModelStreamResponse response, JSONObject usage) {
        if (usage == null) return;
        response.setPromptTokens(usage.getInteger("prompt_tokens"));
        response.setCompletionTokens(usage.getInteger("completion_tokens"));
        response.setTotalTokens(usage.getInteger("total_tokens"));
        JSONObject completionTokensDetails = usage.getJSONObject("completion_tokens_details");
        if (completionTokensDetails != null) {
            response.setReasoningTokens(completionTokensDetails.getInteger("reasoning_tokens"));
        }
    }

    @Override
    public ModelStreamResponse parseStream(InputStream inputStream, String defaultModel, ModelStreamCallback callback) throws IOException {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        ModelStreamResponse response = new ModelStreamResponse();
        response.setModel(defaultModel);
        Map<Integer, JSONObject> toolCallsMap = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (callback != null && callback.isClosed()) break;
                if (!line.startsWith("data:")) continue;
                String data = line.substring("data:".length()).trim();
                if (StringUtils.isBlank(data)) continue;
                raw.append(data).append('\n');
                if ("[DONE]".equals(data)) break;
                parseStreamData(data, defaultModel, callback, content, reasoningContent, response, toolCallsMap);
            }
        }

        response.setContent(content.toString());
        response.setReasoningContent(reasoningContent.toString());
        response.setRawResponse(raw.toString());
        if (!toolCallsMap.isEmpty()) {
            JSONArray toolCallsArray = new JSONArray();
            toolCallsArray.addAll(toolCallsMap.values());
            response.setToolCalls(toolCallsArray.toJSONString());
        }
        return response;
    }

    protected void parseStreamData(String data, String defaultModel, ModelStreamCallback callback,
                                   StringBuilder content, StringBuilder reasoningContent,
                                   ModelStreamResponse response, Map<Integer, JSONObject> toolCallsMap) {
        JSONObject json = JSONObject.parseObject(data);
        response.setModel(StringUtils.defaultIfBlank(json.getString("model"), defaultModel));
        applyUsage(response, json.getJSONObject("usage"));
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return;
        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
        if (delta == null) return;
        String chunk = delta.getString("content");
        if (StringUtils.isNotEmpty(chunk)) {
            content.append(chunk);
            if (callback != null) callback.onMessage(chunk);
        }
        String reasoningChunk = delta.getString("reasoning_content");
        if (StringUtils.isNotEmpty(reasoningChunk)) {
            reasoningContent.append(reasoningChunk);
            if (callback != null) callback.onReasoning(reasoningChunk);
        }
        JSONArray toolCalls = delta.getJSONArray("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            mergeToolCalls(toolCallsMap, toolCalls);
            if (callback != null) callback.onToolCall(toolCalls.toJSONString());
        }
    }

    protected void mergeToolCalls(Map<Integer, JSONObject> toolCallsMap, JSONArray toolCalls) {
        for (int i = 0; i < toolCalls.size(); i++) {
            JSONObject chunk = toolCalls.getJSONObject(i);
            int index = chunk.getIntValue("index");
            JSONObject existing = toolCallsMap.get(index);
            if (existing == null) {
                toolCallsMap.put(index, chunk);
                continue;
            }
            JSONObject existingFunction = existing.getJSONObject("function");
            JSONObject chunkFunction = chunk.getJSONObject("function");
            if (existingFunction != null && chunkFunction != null) {
                String chunkArgs = chunkFunction.getString("arguments");
                if (StringUtils.isNotBlank(chunkArgs)) {
                    existingFunction.put("arguments", StringUtils.defaultString(existingFunction.getString("arguments")) + chunkArgs);
                }
                if (StringUtils.isBlank(existingFunction.getString("name")) && StringUtils.isNotBlank(chunkFunction.getString("name"))) {
                    existingFunction.put("name", chunkFunction.getString("name"));
                }
            }
            if (StringUtils.isBlank(existing.getString("id")) && StringUtils.isNotBlank(chunk.getString("id"))) {
                existing.put("id", chunk.getString("id"));
            }
        }
    }

    @Override
    public Set<String> supportedFeatures() {
        return new LinkedHashSet<>(Arrays.asList("chat", "stream", "tools", "reasoning", "response_format", "usage"));
    }
}
