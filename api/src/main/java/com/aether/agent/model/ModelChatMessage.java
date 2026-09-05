package com.aether.agent.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /**
     * Required by thinking-mode OpenAI-compatible providers when continuing after a tool call.
     */
    private String reasoningContent;

    /**
     * 本轮用户上传的原始文件。仅在支持原生文件输入的供应商请求中序列化。
     */
    private List<ModelInputFile> inputFiles;

    private transient Integer cachedTokens;

    /**
     * 创建 {@code ModelChatMessage} 实例。
     */
    public ModelChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 创建 {@code ModelChatMessage} 实例。
     */
    public ModelChatMessage(String role, String content, String toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    /**
     * 创建 {@code ModelChatMessage} 实例。
     */
    public ModelChatMessage(String role, String content, String toolCalls, String toolCallId, String reasoningContent) {
        this(role, content, toolCalls, toolCallId);
        this.reasoningContent = reasoningContent;
    }
}
