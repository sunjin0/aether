package com.aether.evaluation.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_dataset")
public class EvaluationDataset extends AccountOwnedEntity {
    private String name;
    private String description;
    private String targetType;
    private Long revision;
    private Boolean archived;
}
