package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/** Latest completed context-capacity measurement for a conversation. */
@Data
public class AgentConversationContextVo {
    @ApiModelProperty(value = "Model call ID")
    private String modelCallId;
    @ApiModelProperty(value = "Run ID")
    private String runId;
    @ApiModelProperty(value = "Model context window tokens")
    private Integer contextWindowTokens;
    @ApiModelProperty(value = "Reserved output tokens")
    private Integer outputReserveTokens;
    @ApiModelProperty(value = "Safety reserve tokens")
    private Integer safetyReserveTokens;
    @ApiModelProperty(value = "Usable input budget tokens")
    private Integer inputBudgetTokens;
    @ApiModelProperty(value = "Provider-reported prompt tokens")
    private Integer promptTokens;
    @ApiModelProperty(value = "Provider-reported cached prompt/input tokens")
    private Integer cachedPromptTokens;
    @ApiModelProperty(value = "Provider-reported uncached prompt/input tokens")
    private Integer uncachedPromptTokens;
    @ApiModelProperty(value = "Provider-reported prompt cache hit rate percentage")
    private Double promptCacheHitRate;
    @ApiModelProperty(value = "Estimated prompt tokens")
    private Integer estimatedPromptTokens;
    @ApiModelProperty(value = "Tool definition schema tokens")
    private Integer toolDefinitionTokens;
    @ApiModelProperty(value = "Provider framing/tokenizer delta not covered by message or tool estimates")
    private Integer framingTokens;
    @ApiModelProperty(value = "Input occupancy percentage")
    private Double occupancyPercent;
    @ApiModelProperty(value = "System section tokens")
    private Integer systemTokens;
    @ApiModelProperty(value = "Skill section tokens")
    private Integer skillTokens;
    @ApiModelProperty(value = "Task section tokens")
    private Integer taskTokens;
    @ApiModelProperty(value = "Memory section tokens")
    private Integer memoryTokens;
    @ApiModelProperty(value = "Summary section tokens")
    private Integer summaryTokens;
    @ApiModelProperty(value = "History section tokens")
    private Integer historyTokens;
    @ApiModelProperty(value = "Tool section tokens")
    private Integer toolTokens;
    @ApiModelProperty(value = "RAG section tokens")
    private Integer ragTokens;
    @ApiModelProperty(value = "Current message tokens")
    private Integer currentMessageTokens;
    @ApiModelProperty(value = "Trimmed message count")
    private Integer trimmedMessageCount;
    @ApiModelProperty(value = "Compression status")
    private String compressionStatus;
}
