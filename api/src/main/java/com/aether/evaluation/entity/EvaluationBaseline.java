package com.aether.evaluation.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("evaluation_baseline")
public class EvaluationBaseline extends BaseEntity { private String ownerId,targetType,targetId,datasetVersionId,configHash,experimentId; private Long revision; }
