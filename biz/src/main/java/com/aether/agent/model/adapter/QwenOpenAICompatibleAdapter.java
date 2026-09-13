package com.aether.agent.model.adapter;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelInputFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Qwen/DashScope style OpenAI-compatible adapter with a conservative parameter allowlist.
 */
@Component
public class QwenOpenAICompatibleAdapter extends OpenAIChatAdapter {

    /**
     * Qwen's OpenAI-compatible endpoint accepts a file content item's value as
     * the URL string itself, not the Responses-style {filename,file_url} object.
     */
    @Override
    protected Object toJsonContent(ModelChatMessage message) {
        if (message.getInputFiles() == null || message.getInputFiles().isEmpty()) {
            return super.toJsonContent(message);
        }
        JSONArray content = new JSONArray();
        content.add(new JSONObject().fluentPut("type", "text")
                .fluentPut("text", StringUtils.defaultString(message.getContent(), "")));
        for (ModelInputFile file : message.getInputFiles()) {
            if (file != null && StringUtils.isNotBlank(file.getFileUrl())) {
                if (StringUtils.startsWithIgnoreCase(file.getContentType(), "image/")) {
                    content.add(new JSONObject().fluentPut("type", "image_url")
                            .fluentPut("image_url", new JSONObject().fluentPut("url", file.getFileUrl())));
                } else {
                    content.add(new JSONObject().fluentPut("type", "file").fluentPut("file", file.getFileUrl()));
                }
            }
        }
        return content;
    }

    @Override
    public boolean supports(String providerType) {
        return "qwen-compatible".equalsIgnoreCase(providerType);
    }

    /**
     * DashScope validates every historical tool call before streaming a new
     * response. Its OpenAI-compatible endpoint requires function.arguments to
     * be a JSON object encoded as a string; unlike some compatible providers,
     * it rejects an object value, an empty string, and incomplete fragments.
     */
    @Override
    protected JSONArray normalizeToolCalls(JSONArray toolCalls) {
        JSONArray normalized = super.normalizeToolCalls(toolCalls);
        for (int i = 0; i < normalized.size(); i++) {
            JSONObject toolCall = normalized.getJSONObject(i);
            if (toolCall == null) continue;
            JSONObject function = toolCall.getJSONObject("function");
            if (function == null) continue;
            Object arguments = function.get("arguments");
            try {
                Object parsed = arguments instanceof String ? JSON.parse((String) arguments) : arguments;
                function.put("arguments", parsed instanceof JSONObject ? JSON.toJSONString(parsed) : "{}");
            } catch (Exception ignored) {
                function.put("arguments", "{}");
            }
        }
        return normalized;
    }


    @Override
    protected void applyGenerationParameters(JSONObject body, ModelChatRequest request, AgentDefinition agent, boolean stream) {
        if (request.getTemperature() != null || agent.getTemperature() != null) {
            body.put("temperature", request.getTemperature() != null ? request.getTemperature() : agent.getTemperature());
        }
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
        if (request.getPresencePenalty() != null) body.put("presence_penalty", request.getPresencePenalty());
        if (request.getFrequencyPenalty() != null) body.put("frequency_penalty", request.getFrequencyPenalty());
        if (request.getStop() != null && !request.getStop().isEmpty()) body.put("stop", request.getStop());
        String reasoningEffort = org.apache.commons.lang3.StringUtils.defaultIfBlank(request.getReasoningEffort(),
                Boolean.TRUE.equals(agent.getDefaultThinking()) ? agent.getDefaultReasoningEffort() : null);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(reasoningEffort)) {
            body.put("reasoning_effort", reasoningEffort);
        }
        // Qwen3 hybrid-thinking models default to thinking when omitted. Keep
        // the provider request aligned with the chat-level thinking switch.
        if (supportsThinkingSwitch(resolveModel(request, agent))) {
            body.put("enable_thinking", Boolean.TRUE.equals(agent.getDefaultThinking()));
        }
        applyProviderOptions(body, request);
    }

    private boolean supportsThinkingSwitch(String model) {
        return StringUtils.startsWithIgnoreCase(StringUtils.defaultString(model), "qwen3");
    }

    @Override
    public Set<String> supportedFeatures() {
        return new LinkedHashSet<>(Arrays.asList("chat", "stream", "tools", "reasoning", "usage"));
    }
}
