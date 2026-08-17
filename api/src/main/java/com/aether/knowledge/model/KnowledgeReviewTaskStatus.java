package com.aether.knowledge.model;

/**
 * 表示知识库审核任务状态。
 */
public final class KnowledgeReviewTaskStatus {
    public static final String PENDING = "pending";
    public static final String CLAIMED = "claimed";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String CANCELLED = "cancelled";

    /**
     * 判断是否为Valid。
     */
    public static boolean isValid(String value) {
        return PENDING.equals(value) || CLAIMED.equals(value) || APPROVED.equals(value)
                || REJECTED.equals(value) || CANCELLED.equals(value);
    }

    /**
     * 创建 {@code KnowledgeReviewTaskStatus} 实例。
     */
    private KnowledgeReviewTaskStatus() {
    }
}
