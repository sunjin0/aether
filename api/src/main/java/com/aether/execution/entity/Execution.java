package com.aether.execution.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** Unified execution ledger for agent, workflow, tool and child-agent runs. */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("aether_execution")
public class Execution extends BaseEntity {
    /** Tenant boundary captured when the execution is created. */
    private String tenantId;
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
    private java.math.BigDecimal estimatedCost;
    private String model;
    private String errorCode;
    private String errorMessage;
    private String metadata;
}
