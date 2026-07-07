package com.aether.agent.model;

/**
 * 模型流式输出回调。
 */
public interface ModelStreamCallback {

    void onMessage(String chunk);

    void onReasoning(String chunk);

    void onToolCall(String toolCallJson);

    boolean isClosed();
}
