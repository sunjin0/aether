package com.aether.execution.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExecutionTraceSummaryVo {
    private String traceId;
    private long executionCount;
    private long succeededCount;
    private long failedCount;
    private long waitingCount;
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private long totalDurationMs;
    private BigDecimal estimatedCost = BigDecimal.ZERO;
}
