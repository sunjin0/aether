package com.aether.agent.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 模型流式输出最终响应。
 */
@Data
public class ModelStreamResponse {

    private String runId;

    private String content;

    private String reasoningContent;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer reasoningTokens;

    private Integer cachedPromptTokens;

    private Integer uncachedPromptTokens;

    private Double promptCacheHitRate;

    /**
     * 工具调用请求（JSON格式，assistant角色时）
     */
    private String toolCalls;

    private String rawResponse;

    private Boolean waitingUser;

    private List<Map<String, Object>> sources;
}
