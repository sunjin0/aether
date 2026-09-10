package com.aether.evaluation.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.evaluation.handler.JsonbStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "evaluation_case", autoResultMap = true)
public class EvaluationCase extends BaseEntity {
    private String datasetId;
    private String caseKey;
    private String name;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String inputJson;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String referenceJson;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String assertionsJson;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String evaluatorBindingsJson;
    @TableField(typeHandler = JsonbStringTypeHandler.class) private String tagsJson;
    private Boolean enabled;
    private Boolean required;
    private Integer passThreshold;
    private Long revision;
}
