package com.aether.knowledge.model;

public final class KnowledgeAiReviewIssueStatus {
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";
    public static final String MANUALLY_FIXED = "manually_fixed";
    public static final String IGNORED = "ignored";

    public static boolean isManualResolution(String value) {
        return REJECTED.equals(value) || MANUALLY_FIXED.equals(value) || IGNORED.equals(value);
    }

    private KnowledgeAiReviewIssueStatus() { }
}
