package com.aether.evaluation.entity;
import com.aether.entity.AccountOwnedEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("evaluation_baseline")
public class EvaluationBaseline extends AccountOwnedEntity { private String targetType,targetId,datasetVersionId,configHash,experimentId; private Long revision; }
