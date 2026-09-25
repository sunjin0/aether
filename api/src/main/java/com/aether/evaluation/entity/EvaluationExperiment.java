package com.aether.evaluation.entity;
import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.evaluation.handler.JsonbStringTypeHandler;
import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName(value = "evaluation_experiment", autoResultMap = true)
public class EvaluationExperiment extends AccountOwnedEntity {
 private String name, targetType, targetId, snapshotId, targetFingerprint, datasetVersionId, configHash, scope, status, qualityStatus, requestKey, parentExperimentId;
 @TableField(typeHandler = JsonbStringTypeHandler.class) private String selectionJson;
 @TableField(typeHandler = JsonbStringTypeHandler.class) private String configJson;
 @TableField(typeHandler = JsonbStringTypeHandler.class) private String countsJson;
 @TableField(typeHandler = JsonbStringTypeHandler.class) private String metricsJson;
 private Long revision, reportRevision, startedAt, finishedAt;
}
