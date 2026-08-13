package com.aether.agent.service;

import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.vo.AgentMessageVo;

/**
 * Agent流式聊天回调。
 */
public interface AgentStreamCallback {

    void onMessage(String conversationId, String chunk);

    void onReasoning(String conversationId, String chunk);

    void onToolCall(String conversationId, String toolCallJson);

    void onQuestion(String conversationId, String runId, AgentMessageVo question);

    void onDone(String conversationId, String messageId, ModelStreamResponse response);

    void onError(int code, String message);

    boolean isClosed();

    /** Emits a non-terminal progress stage before the first model token is available. */
    default void onStatus(String stage, String message) {}

    default void onRunStep(String runId, String stepJson) {}
}
