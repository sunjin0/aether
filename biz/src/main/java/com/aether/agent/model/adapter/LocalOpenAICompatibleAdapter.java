package com.aether.agent.model.adapter;

import org.springframework.stereotype.Component;

/**
 * Adapter boundary for self-hosted OpenAI-compatible servers.
 *
 * <p>It intentionally starts with the common OpenAI wire protocol, while
 * keeping local deployments isolated so their input/output quirks can be
 * adapted without changing the OpenAI, Qwen, Azure, or Anthropic paths.</p>
 */
@Component
public class LocalOpenAICompatibleAdapter extends OpenAIChatAdapter {

    @Override
    public boolean supports(String providerType) {
        return "local".equalsIgnoreCase(providerType)
                || "local-openai-compatible".equalsIgnoreCase(providerType);
    }
}
