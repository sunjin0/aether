package com.aether.evaluation.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_dataset")
public class EvaluationDataset extends BaseEntity {
    private String name;
    private String description;
    private String targetType;
    private String ownerId;
    private Long revision;
    private Boolean archived;
}
