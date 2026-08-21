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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for Anthropic Messages API.
 */
@Component
public class AnthropicChatAdapter implements ModelProviderAdapter {

    private static final String DEFAULT_VERSION = "2023-06-01";

    @Override
    public boolean supports(String providerType) {
        return "anthropic".equalsIgnoreCase(providerType);
    }

    @Override
    public String chatUrl(ModelChatRequest request) {
        ModelProvider provider = request.getProvider();
        if (provider == null || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.model.api.base.url.required"));
        }
        String base = StringUtils.removeEnd(provider.getApiBaseUrl(), "/");
        return base.endsWith("/v1/messages") ? base : base + "/v1/messages";
    }

    @Override
    public HttpHeaders headers(ModelChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        streamHeaders(request).forEach(headers::set);
        return headers;
    }

    @Override
    public Map<String, String> streamHeaders(ModelChatRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        ModelProvider provider = request.getProvider();
        if (provider != null && StringUtils.isNotBlank(provider.getApiKey())) {
            headers.put("x-api-key", AesUtil.decrypt(provider.getApiKey()));
        }
        headers.put("anthropic-version", StringUtils.defaultIfBlank(option(request, "anthropicVersion"), DEFAULT_VERSION));
        String beta = option(request, "anthropicBeta");
        if (StringUtils.isNotBlank(beta)) headers.put("anthropic-beta", beta);
        return headers;
    }

    @Override
    public JSONObject body(ModelChatRequest request, boolean stream) {
        AgentDefinition agent = request.getAgent() != null ? request.getAgent() : new AgentDefinition();
        JSONObject body = new JSONObject();
        body.put("model", StringUtils.defaultIfBlank(request.getModel(), StringUtils.defaultIfBlank(agent.getModel(), "claude-3-5-sonnet-latest")));
        body.put("messages", messages(request.getMessages(), body));
        Integer maxTokens = request.getMaxCompletionTokens() != null ? request.getMaxCompletionTokens() : request.getMaxTokens();
        if (maxTokens == null) maxTokens = agent.getMaxTokens();
        body.put("max_tokens", maxTokens == null ? 1024 : maxTokens);
        body.put("stream", stream);
        if (request.getTemperature() != null || agent.getTemperature() != null) {
            body.put("temperature", request.getTemperature() != null ? request.getTemperature() : agent.getTemperature());
        }
        if (request.getTopP() != null) body.put("top_p", request.getTopP());
        if (request.getStop() != null && !request.getStop().isEmpty()) body.put("stop_sequences", request.getStop());
        applyThinking(body, request, agent);
        applyTools(body, request);
        return body;
    }

    private JSONArray messages(List<ModelChatMessage> messages, JSONObject body) {
        JSONArray array = new JSONArray();
        if (messages == null) return array;
        for (ModelChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                if (StringUtils.isNotBlank(message.getContent())) body.put("system", message.getContent());
                continue;
            }
            JSONObject item = new JSONObject();
            if (StringUtils.isNotBlank(message.getToolCallId())) {
                item.put("role", "user");
                item.put("content", new JSONArray().fluentAdd(new JSONObject()
                        .fluentPut("type", "tool_result")
                        .fluentPut("tool_use_id", message.getToolCallId())
                        .fluentPut("content", StringUtils.defaultString(message.getContent()))));
            } else {
                item.put("role", "assistant".equals(message.getRole()) ? "assistant" : "user");
                item.put("content", assistantContent(message));
            }
            array.add(item);
        }
        return array;
    }

    private Object assistantContent(ModelChatMessage message) {
        if (!"assistant".equals(message.getRole()) || StringUtils.isBlank(message.getToolCalls())) {
            return StringUtils.defaultString(message.getContent());
        }
        JSONArray content = new JSONArray();
        if (StringUtils.isNotBlank(message.getContent())) {
            content.add(new JSONObject().fluentPut("type", "text").fluentPut("text", message.getContent()));
        }
        JSONArray toolCalls = JSONArray.parseArray(message.getToolCalls());
        for (int i = 0; i < toolCalls.size(); i++) {
            JSONObject call = toolCalls.getJSONObject(i);
            JSONObject function = call.getJSONObject("function");
            content.add(new JSONObject()
                    .fluentPut("type", "tool_use")
                    .fluentPut("id", call.getString("id"))
                    .fluentPut("name", function == null ? null : function.getString("name"))
                    .fluentPut("input", parseToolArguments(function == null ? null : function.getString("arguments"))));
        }
        return content;
    }

    private Object parseToolArguments(String arguments) {
        if (StringUtils.isBlank(arguments)) return new JSONObject();
        try {
            return JSONObject.parseObject(arguments);
        } catch (Exception e) {
            return new JSONObject().fluentPut("value", arguments);
        }
    }

    private void applyThinking(JSONObject body, ModelChatRequest request, AgentDefinition agent) {
        String effort = StringUtils.defaultIfBlank(request.getReasoningEffort(),
                Boolean.TRUE.equals(agent.getDefaultThinking()) ? agent.getDefaultReasoningEffort() : null);
        if (StringUtils.isBlank(effort) || "none".equalsIgnoreCase(effort)) return;
        int budget = "high".equalsIgnoreCase(effort) ? 4096 : ("medium".equalsIgnoreCase(effort) ? 2048 : 1024);
        body.put("thinking", new JSONObject().fluentPut("type", "enabled").fluentPut("budget_tokens", budget));
    }

    private void applyTools(JSONObject body, ModelChatRequest request) {
        JSONArray tools = new JSONArray();
        if (request.getTools() != null) {
            for (AgentTool tool : request.getTools()) {
                if (tool == null || StringUtils.isBlank(tool.getCode())) continue;
                JSONObject item = new JSONObject();
                item.put("name", tool.getName());
                item.put("description", StringUtils.defaultIfBlank(tool.getDescription(), tool.getCode()));
                String schema = StringUtils.defaultIfBlank(tool.getParametersSchema(), tool.getMcpInputSchema());
                item.put("input_schema", StringUtils.isBlank(schema) ? new JSONObject()
                        .fluentPut("type", "object").fluentPut("properties", new JSONObject()) : JSONObject.parseObject(schema));
                tools.add(item);
            }
        }
        if (tools.isEmpty()) return;
        body.put("tools", tools);
        if (StringUtils.isNotBlank(request.getToolChoiceName())) {
            body.put("tool_choice", new JSONObject().fluentPut("type", "tool").fluentPut("name", request.getToolChoiceName()));
        } else if ("required".equalsIgnoreCase(request.getToolChoice())) {
            body.put("tool_choice", new JSONObject().fluentPut("type", "any"));
        } else if ("none".equalsIgnoreCase(request.getToolChoice())) {
            body.put("tool_choice", new JSONObject().fluentPut("type", "none"));
        } else {
            body.put("tool_choice", new JSONObject().fluentPut("type", "auto"));
        }
    }

    @Override
    public ModelChatResponse parseResponse(String responseBody, String defaultModel) {
        JSONObject json = parseJson(responseBody);
        if (json.getJSONObject("error") != null) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.provider.error"));
        }
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        JSONArray toolCalls = new JSONArray();
        JSONArray blocks = json.getJSONArray("content");
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                appendBlock(block, content, reasoning, toolCalls);
            }
        }
        if (content.length() == 0 && reasoning.length() == 0 && toolCalls.isEmpty()) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.empty"));
        }
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(content.toString());
        response.setReasoningContent(reasoning.length() == 0 ? null : reasoning.toString());
        if (!toolCalls.isEmpty()) response.setToolCalls(toolCalls.toJSONString());
        response.setModel(StringUtils.defaultIfBlank(json.getString("model"), defaultModel));
        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            response.setPromptTokens(usage.getInteger("input_tokens"));
            response.setCompletionTokens(usage.getInteger("output_tokens"));
            if (response.getPromptTokens() != null && response.getCompletionTokens() != null) {
                response.setTotalTokens(response.getPromptTokens() + response.getCompletionTokens());
            }
        }
        response.setRawResponse(responseBody);
        return response;
    }

    private void appendBlock(JSONObject block, StringBuilder content, StringBuilder reasoning, JSONArray toolCalls) {
        if (block == null) return;
        String type = block.getString("type");
        if ("text".equals(type)) {
            content.append(StringUtils.defaultString(block.getString("text")));
        } else if ("thinking".equals(type)) {
            reasoning.append(StringUtils.defaultString(block.getString("thinking")));
        } else if ("tool_use".equals(type)) {
            toolCalls.add(new JSONObject()
                    .fluentPut("id", block.getString("id"))
                    .fluentPut("type", "function")
                    .fluentPut("function", new JSONObject()
                            .fluentPut("name", block.getString("name"))
                            .fluentPut("arguments", block.getJSONObject("input") == null ? "{}" : block.getJSONObject("input").toJSONString())));
        }
    }

    @Override
    public ModelStreamResponse parseStream(InputStream inputStream, String defaultModel, ModelStreamCallback callback) throws IOException {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        ModelStreamResponse response = new ModelStreamResponse();
        response.setModel(defaultModel);
        Map<Integer, JSONObject> toolCalls = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String event = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (callback != null && callback.isClosed()) break;
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    String data = line.substring("data:".length()).trim();
                    if (StringUtils.isBlank(data) || "[DONE]".equals(data)) continue;
                    raw.append(data).append('\n');
                    parseStreamEvent(event, data, response, content, reasoning, toolCalls, callback);
                }
            }
        }
        response.setContent(content.toString());
        response.setReasoningContent(reasoning.length() == 0 ? null : reasoning.toString());
        if (!toolCalls.isEmpty()) {
            JSONArray array = new JSONArray();
            array.addAll(toolCalls.values());
            response.setToolCalls(array.toJSONString());
        }
        response.setRawResponse(raw.toString());
        return response;
    }

    private void parseStreamEvent(String event, String data, ModelStreamResponse response, StringBuilder content,
                                  StringBuilder reasoning, Map<Integer, JSONObject> toolCalls, ModelStreamCallback callback) {
        JSONObject json = parseJson(data);
        if ("message_start".equals(event) && json.getJSONObject("message") != null) {
            JSONObject message = json.getJSONObject("message");
            response.setModel(StringUtils.defaultIfBlank(message.getString("model"), response.getModel()));
            JSONObject usage = message.getJSONObject("usage");
            if (usage != null) {
                response.setPromptTokens(usage.getInteger("input_tokens"));
            }
            return;
        }
        if ("content_block_start".equals(event) && json.getJSONObject("content_block") != null) {
            JSONObject block = json.getJSONObject("content_block");
            if ("tool_use".equals(block.getString("type"))) {
                int index = json.getIntValue("index");
                toolCalls.put(index, new JSONObject()
                        .fluentPut("id", block.getString("id"))
                        .fluentPut("type", "function")
                        .fluentPut("function", new JSONObject()
                                .fluentPut("name", block.getString("name"))
                                .fluentPut("arguments", "")));
            }
            return;
        }
        if ("content_block_delta".equals(event) && json.getJSONObject("delta") != null) {
            JSONObject delta = json.getJSONObject("delta");
            String text = delta.getString("text");
            if (StringUtils.isNotEmpty(text)) {
                content.append(text);
                if (callback != null) callback.onMessage(text);
            }
            String thinking = delta.getString("thinking");
            if (StringUtils.isNotEmpty(thinking)) {
                reasoning.append(thinking);
                if (callback != null) callback.onReasoning(thinking);
            }
            String partialJson = delta.getString("partial_json");
            if (StringUtils.isNotEmpty(partialJson)) {
                JSONObject call = toolCalls.get(json.getIntValue("index"));
                if (call != null) {
                    JSONObject function = call.getJSONObject("function");
                    function.put("arguments", StringUtils.defaultString(function.getString("arguments")) + partialJson);
                    if (callback != null) callback.onToolCall(new JSONArray().fluentAdd(call).toJSONString());
                }
            }
            return;
        }
        if ("message_delta".equals(event) && json.getJSONObject("usage") != null) {
            JSONObject usage = json.getJSONObject("usage");
            response.setCompletionTokens(usage.getInteger("output_tokens"));
            if (response.getPromptTokens() != null && response.getCompletionTokens() != null) {
                response.setTotalTokens(response.getPromptTokens() + response.getCompletionTokens());
            }
        }
    }

    private JSONObject parseJson(String body) {
        try {
            JSONObject json = JSONObject.parseObject(body);
            if (json == null) throw new IllegalArgumentException("empty json");
            return json;
        } catch (Exception e) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.invalid"));
        }
    }

    private String option(ModelChatRequest request, String name) {
        if (request.getProviderOptions() == null) return null;
        Object value = request.getProviderOptions().get(name);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public Set<String> supportedFeatures() {
        return new LinkedHashSet<>(Arrays.asList("chat", "stream", "tools", "reasoning", "usage"));
    }
}
