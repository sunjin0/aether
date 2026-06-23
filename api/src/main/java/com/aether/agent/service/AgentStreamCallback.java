package com.aether.agent.service;

import com.aether.agent.model.ModelStreamResponse;

/**
 * Agent流式聊天回调。
 */
public interface AgentStreamCallback {

    void onMessage(String conversationId, String chunk);

    void onToolCall(String conversationId, String toolCallJson);

    void onDone(String conversationId, String messageId, ModelStreamResponse response);

    void onError(int code, String message);

    boolean isClosed();
}
