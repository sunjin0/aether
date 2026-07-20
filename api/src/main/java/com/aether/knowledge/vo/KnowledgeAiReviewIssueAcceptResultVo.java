package com.aether.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeAiReviewIssueAcceptResultVo {
    private String documentVersionId;
    private String contentChecksum;
    private String reviewStatus;
    private String issueStatus;
    private boolean requiresAiReview;
}
