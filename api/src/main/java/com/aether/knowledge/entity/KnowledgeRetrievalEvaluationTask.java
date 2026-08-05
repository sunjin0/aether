package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A durable single-query retrieval evaluation task. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_task")
public class KnowledgeRetrievalEvaluationTask extends BaseEntity {
    private String runId;
    private String evaluationCaseId;
    private String questionSnapshot;
    private String targetTypeSnapshot;
    private String expectedChunkIdsSnapshot;
    private String expectedDocumentIdSnapshot;
    private String expectedSectionPathSnapshot;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String errorCode;
    private String errorMessage;
    private Long startedAt;
    private Long finishedAt;
}
