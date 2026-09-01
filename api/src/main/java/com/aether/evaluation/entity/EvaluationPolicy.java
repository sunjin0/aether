package com.aether.evaluation.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Release gate configuration for an Agent platform resource. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_evaluation_policy")
public class EvaluationPolicy extends BaseEntity {
    private String targetType;
    private String targetId;
    private Boolean required;
    private Integer minimumScore;
    private Integer lastScore;
    private String lastStatus;
    private String lastRunId;
    private Long evaluatedAt;
}
