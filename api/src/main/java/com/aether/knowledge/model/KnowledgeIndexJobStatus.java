package com.aether.knowledge.model;

/**
 * 表示知识库索引Job状态。
 */
public final class KnowledgeIndexJobStatus {
    public static final String PENDING = "pending";
    public static final String RUNNING = "running";
    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";

    /**
     * 创建 {@code KnowledgeIndexJobStatus} 实例。
     */
    private KnowledgeIndexJobStatus() {
    }
}
