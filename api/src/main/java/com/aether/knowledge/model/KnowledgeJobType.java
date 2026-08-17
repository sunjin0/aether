package com.aether.knowledge.model;

/**
 * 表示知识库JobType。
 */
public final class KnowledgeJobType {
    public static final String CREATE = "create";
    public static final String UPLOAD = "upload";
    public static final String UPDATE = "update";
    public static final String REINDEX = "reindex";
    public static final String ROLLBACK = "rollback";
    public static final String RETRY = "retry";

    /**
     * 创建 {@code KnowledgeJobType} 实例。
     */
    private KnowledgeJobType() {
    }
}
