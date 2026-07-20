package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_ai_review")
public class KnowledgeAiReview extends BaseEntity {
    private String knowledgeBaseId;
    private String documentId;
    private String documentVersionId;
    private String sourceChecksum;
    /** Immutable document content snapshot at the time this AI review was started. */
    private String sourceContent;
    private String modelProviderId;
    private String model;
    private String promptVersion;
    /** pending/running/success/failed/stale */
    private String status;
    private Integer score;
    private String summary;
    private String issues;
    private String statistics;
    private String errorMessage;
    private Long startedAt;
    private Long finishedAt;
}
