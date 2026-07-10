package com.aether.agent.model;

import lombok.Data;

/**
 * 模型流式输出最终响应。
 */
@Data
public class ModelStreamResponse {

    private String content;

    private String reasoningContent;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer reasoningTokens;

    /**
     * 工具调用请求（JSON格式，assistant角色时）
     */
    private String toolCalls;

    private String rawResponse;
}
