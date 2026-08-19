package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Immutable context budget snapshot for one model dispatch phase.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode
@TableName("agent_run_context_metric")
public class AgentRunContextMetric {
    @TableId
    private String modelCallId;
    private String sourceModelCallId;
    private String runId;
    private String callType;
    private Integer attemptNo;
    private String metricPhase;
    private Integer contextWindowTokens;
    private Integer outputReserveTokens;
    private Integer safetyReserveTokens;
    private Integer inputBudgetTokens;
    private Integer promptTokens;
    private Integer estimatedPromptTokens;
    private Integer systemTokens;
    private Integer skillTokens;
    private Integer taskTokens;
    private Integer memoryTokens;
    private Integer summaryTokens;
    private Integer historyTokens;
    private Integer toolTokens;
    private Integer toolDefinitionTokens;
    private Integer ragTokens;
    private Integer currentMessageTokens;
    private Integer trimmedMessageCount;
    private Integer compressedMessageCount;
    private String compressionStatus;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Long createdAt;
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
}
