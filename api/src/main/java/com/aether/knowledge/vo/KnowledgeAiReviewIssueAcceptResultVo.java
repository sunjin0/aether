package com.aether.knowledge.vo;

import lombok.Data;

/**
 * 表示知识库Ai审核IssueAccept结果VO。
 */
@Data
public class KnowledgeAiReviewIssueAcceptResultVo {
    private String documentVersionId;
    private String contentChecksum;
    private String reviewStatus;
    private String issueStatus;
    private boolean requiresAiReview;
}
