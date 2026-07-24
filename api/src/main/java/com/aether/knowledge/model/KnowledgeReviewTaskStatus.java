package com.aether.knowledge.model;

public final class KnowledgeReviewTaskStatus {
    public static final String PENDING = "pending";
    public static final String CLAIMED = "claimed";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String CANCELLED = "cancelled";

    public static boolean isValid(String value) {
        return PENDING.equals(value) || CLAIMED.equals(value) || APPROVED.equals(value)
                || REJECTED.equals(value) || CANCELLED.equals(value);
    }

    private KnowledgeReviewTaskStatus() { }
}
