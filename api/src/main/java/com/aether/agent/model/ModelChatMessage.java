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
}
