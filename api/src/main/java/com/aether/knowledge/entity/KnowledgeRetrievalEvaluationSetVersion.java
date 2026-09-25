package com.aether.knowledge.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Immutable published dataset snapshot used as a reproducible evaluation input.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_set_version")
public class KnowledgeRetrievalEvaluationSetVersion extends AccountOwnedEntity {
    private String evaluationSetId;
    private Integer versionNo;
    private String snapshotJson;
    private Long publishedAt;
}
