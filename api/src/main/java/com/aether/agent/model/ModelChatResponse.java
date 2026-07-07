package com.aether.agent.model;

import lombok.Data;

/**
 * 模型聊天响应。
 */
@Data
public class ModelChatResponse {

    private String content;

    private String reasoningContent;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer reasoningTokens;

    private String toolCalls;

    private String rawResponse;
}
