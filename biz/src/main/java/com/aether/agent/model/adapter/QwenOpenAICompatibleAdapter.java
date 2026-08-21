package com.aether.agent.model.adapter;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.model.ModelChatRequest;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Qwen/DashScope style OpenAI-compatible adapter with a conservative parameter allowlist.
 */
@Component
public class QwenOpenAICompatibleAdapter extends OpenAIChatAdapter {

    @Override
    public boolean supports(String providerType) {
        return "qwen-compatible".equalsIgnoreCase(providerType);
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
        applyProviderOptions(body, request);
    }

    @Override
    public Set<String> supportedFeatures() {
        return new LinkedHashSet<>(Arrays.asList("chat", "stream", "tools", "reasoning", "usage"));
    }
}
