package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_ai_review_issue")
public class KnowledgeAiReviewIssue extends BaseEntity {
    private String aiReviewId;
    private String documentVersionId;
    private String blockId;
    private String issueType;
    private String severity;
    private String message;
    private String originalExcerpt;
    private String suggestedPatch;
    /** pending/rejected/manually_fixed/ignored */
    private String handleStatus;
    private String handledBy;
    private Long handledAt;
    private String handleComment;
}
