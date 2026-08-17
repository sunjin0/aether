package com.aether.agent.model;

/**
 * 模型流式输出回调。
 */
public interface ModelStreamCallback {

    /**
     * 处理on消息。
     */
    void onMessage(String chunk);

    /**
     * 处理onReasoning。
     */
    void onReasoning(String chunk);

    /**
     * 处理onToolCall。
     */
    void onToolCall(String toolCallJson);

    /**
     * 判断是否为Closed。
     */
    boolean isClosed();
}
