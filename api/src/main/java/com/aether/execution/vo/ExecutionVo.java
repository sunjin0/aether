package com.aether.execution.vo;

import lombok.Data;

import java.math.BigDecimal;

/** Safe, read-only execution trace projection. */
@Data
public class ExecutionVo {
    private String id;
    private String executionType;
    private String parentExecutionId;
    private String traceId;
    private String applicationId;
    private String actorId;
    private String resourceId;
    private String status;
    private Long startedAt;
    private Long endedAt;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal estimatedCost;
    private String model;
    private String errorCode;
    private String errorMessage;
    private String metadata;
}
