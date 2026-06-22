package com.aether.agent.model;

/**
 * 模型客户端接口。
 */
public interface ModelClient {

    boolean supports(String providerType);

    ModelChatResponse chat(ModelChatRequest request);
}
