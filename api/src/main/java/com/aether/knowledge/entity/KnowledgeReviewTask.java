package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_review_task")
public class KnowledgeReviewTask extends BaseEntity {
    private String knowledgeBaseId;
    private String documentId;
    private String documentVersionId;
    private String submitterId;
    private String reviewerId;
    /** pending/claimed/approved/rejected/cancelled */
    private String status;
    private String sourceChecksum;
    private String submitComment;
    private String reviewComment;
    private Long submittedAt;
    private Long claimedAt;
    private Long reviewedAt;
}
