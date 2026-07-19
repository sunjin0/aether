package com.aether.agent.model;

/**
 * 模型客户端接口。
 */
public interface ModelClient {

    boolean supports(String providerType);
    /**
     * 聊天。
     *
     * @param request 请求 agent.model.ModelChatRequest
     * @return 响应。
     */
    ModelChatResponse chat(ModelChatRequest request);

    ModelChatResponse chatByProvider(ModelChatRequest request);

    ModelStreamResponse stream(ModelChatRequest request, ModelStreamCallback callback);
}
