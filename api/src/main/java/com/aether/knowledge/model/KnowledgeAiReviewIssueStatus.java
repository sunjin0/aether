package com.aether.knowledge.model;

/**
 * 表示知识库Ai审核Issue状态。
 */
public final class KnowledgeAiReviewIssueStatus {
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";
    public static final String MANUALLY_FIXED = "manually_fixed";
    public static final String IGNORED = "ignored";

    /**
     * 判断是否为ManualResolution。
     */
    public static boolean isManualResolution(String value) {
        return REJECTED.equals(value) || MANUALLY_FIXED.equals(value) || IGNORED.equals(value);
    }

    /**
     * 创建 {@code KnowledgeAiReviewIssueStatus} 实例。
     */
    private KnowledgeAiReviewIssueStatus() {
    }
}
