package com.aether.knowledge.model;

/**
 * 表示知识库审核状态。
 */
public final class KnowledgeReviewStatus {
    public static final String DRAFT = "DRAFT";
    public static final String AI_REVIEWING = "AI_REVIEWING";
    public static final String AI_REVIEWED = "AI_REVIEWED";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    /**
     * 创建 {@code KnowledgeReviewStatus} 实例。
     */
    private KnowledgeReviewStatus() {
    }
}
