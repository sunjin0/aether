package com.aether.agent.model;

/**
 * 模型客户端接口。
 */
public interface ModelClient {

    /**
     * 判断当前客户端是否支持指定模型提供商类型。
     */
    boolean supports(String providerType);

    /**
     * 聊天。
     *
     * @param request 请求 agent.model.ModelChatRequest
     * @return 响应。
     */
    ModelChatResponse chat(ModelChatRequest request);

    /**
     * 根据请求指定的模型提供商执行非流式对话。
     */
    ModelChatResponse chatByProvider(ModelChatRequest request);

    /**
     * 执行流式对话，并通过回调持续返回增量消息、推理内容和工具调用。
     */
    ModelStreamResponse stream(ModelChatRequest request, ModelStreamCallback callback);
}
