package com.aether.evaluation.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.evaluation.handler.JsonbStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "evaluation_target_snapshot", autoResultMap = true)
public class EvaluationTargetSnapshot extends AccountOwnedEntity {
    private String targetType;
    private String targetId;
    private String sourceKind;
    private String sourceVersionId;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String snapshotJson;
    private String fingerprint;
}
