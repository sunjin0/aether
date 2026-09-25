package com.aether.evaluation.entity;
import com.aether.entity.AccountOwnedEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("evaluation_dataset_version")
public class EvaluationDatasetVersion extends AccountOwnedEntity { private String datasetId, contentHash, publishedBy; private Integer versionNo, caseCount; private Long publishedAt; }
