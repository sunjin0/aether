package com.aether.agent.model.adapter;

import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelStreamCallback;
import com.aether.agent.model.ModelStreamResponse;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Protocol adapter for one family of chat model providers.
 */
public interface ModelProviderAdapter {

    boolean supports(String providerType);

    /**
     * Selects an adapter from the complete provider record. Most providers are
     * identified by type alone; OpenAI-compatible vendors may additionally
     * require endpoint or provider-name detection.
     */
    default boolean supports(ModelProvider provider) {
        return provider != null && supports(provider.getType());
    }

    String chatUrl(ModelChatRequest request);

    HttpHeaders headers(ModelChatRequest request);

    Map<String, String> streamHeaders(ModelChatRequest request);

    JSONObject body(ModelChatRequest request, boolean stream);

    ModelChatResponse parseResponse(String responseBody, String defaultModel);

    ModelStreamResponse parseStream(InputStream inputStream, String defaultModel,
                                    ModelStreamCallback callback) throws IOException;

    Set<String> supportedFeatures();
}
