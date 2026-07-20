package com.aether.knowledge.vo;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeAiReviewDiffVo {
    private String reviewId;
    private String documentId;
    private String documentVersionId;
    private String contentChecksum;
    private String reviewStatus;
    private boolean stale;
    private String originalContent;
    private String proposedContent;
    private List<KnowledgeAiReviewDiffIssueVo> issues;
    private Integer pendingCount;
    private Integer acceptedCount;
    private Integer rejectedCount;
    private Integer criticalPendingCount;
}
