package com.aether.knowledge.model;

/**
 * 表示知识库审核Action。
 */
public final class KnowledgeReviewAction {
    public static final String SUBMITTED = "SUBMITTED";
    public static final String CLAIMED = "CLAIMED";
    public static final String EDITED = "EDITED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    /**
     * 创建 {@code KnowledgeReviewAction} 实例。
     */
    private KnowledgeReviewAction() {
    }
}
