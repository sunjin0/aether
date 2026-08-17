package com.aether.knowledge.model;

/**
 * 表示知识库Ai审核状态。
 */
public final class KnowledgeAiReviewStatus {
    public static final String PENDING = "pending";
    public static final String RUNNING = "running";
    public static final String SUCCESS = "success";
    public static final String STALE = "stale";
    public static final String FAILED = "failed";

    /**
     * 创建 {@code KnowledgeAiReviewStatus} 实例。
     */
    private KnowledgeAiReviewStatus() {
    }
}
