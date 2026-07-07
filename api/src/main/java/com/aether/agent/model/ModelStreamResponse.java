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

    private String rawResponse;
}
