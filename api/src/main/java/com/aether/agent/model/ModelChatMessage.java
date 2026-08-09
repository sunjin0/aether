package com.aether.agent.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型上下文消息。
 */
@Data
@NoArgsConstructor
public class ModelChatMessage {

    private String role;

    private String content;

    private String toolCalls;

    private String toolCallId;

    /** Required by thinking-mode OpenAI-compatible providers when continuing after a tool call. */
    private String reasoningContent;

    private transient Integer cachedTokens;

    public ModelChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ModelChatMessage(String role, String content, String toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    public ModelChatMessage(String role, String content, String toolCalls, String toolCallId, String reasoningContent) {
        this(role, content, toolCalls, toolCallId);
        this.reasoningContent = reasoningContent;
    }
}
