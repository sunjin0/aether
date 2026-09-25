package com.aether.evaluation.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.evaluation.handler.JsonbStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "evaluation_evaluator", autoResultMap = true)
public class EvaluationEvaluator extends AccountOwnedEntity {
    private String name;
    private String kind;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String draftConfigJson;
    private Long revision;
    private Boolean archived;
}
