package com.aether.agent.service;

import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.vo.AgentMessageVo;

/**
 * Agent流式聊天回调。
 */
public interface AgentStreamCallback {

    /**
     * 处理on消息。
     */
    void onMessage(String conversationId, String chunk);

    /**
     * 处理onReasoning。
     */
    void onReasoning(String conversationId, String chunk);

    /**
     * 处理onToolCall。
     */
    void onToolCall(String conversationId, String toolCallJson);

    /**
     * 处理onQuestion。
     */
    void onQuestion(String conversationId, String runId, AgentMessageVo question);

    /**
     * 处理onDone。
     */
    void onDone(String conversationId, String messageId, ModelStreamResponse response);

    /**
     * 处理onError。
     */
    void onError(int code, String message);

    /**
     * 判断是否为Closed。
     */
    boolean isClosed();

    /**
     * Emits a non-terminal progress stage before the first model token is available.
     */
    default void onStatus(String stage, String message) {
    }

    /**
     * 处理on运行Step。
     */
    default void onRunStep(String runId, String stepJson) {
    }
}
