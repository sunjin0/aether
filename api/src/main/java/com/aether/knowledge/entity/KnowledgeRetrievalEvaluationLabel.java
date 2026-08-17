package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One positive retrieval target for an evaluation case.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_label")
public class KnowledgeRetrievalEvaluationLabel extends BaseEntity {
    private String evaluationCaseId;
    private String targetType;
    private String documentId;
    private String sectionPath;
    private String chunkId;
    private Integer relevanceGrade;
    private Boolean isRequired;
    private String remark;
    private Integer status;
}
