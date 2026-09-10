package com.aether.evaluation.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("evaluation_dataset_version")
public class EvaluationDatasetVersion extends BaseEntity { private String datasetId, contentHash, publishedBy; private Integer versionNo, caseCount; private Long publishedAt; }
