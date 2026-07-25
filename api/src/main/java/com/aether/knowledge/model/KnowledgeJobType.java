package com.aether.knowledge.model;

public final class KnowledgeJobType {
    public static final String CREATE = "create";
    public static final String UPLOAD = "upload";
    public static final String UPDATE = "update";
    public static final String REINDEX = "reindex";
    public static final String ROLLBACK = "rollback";
    public static final String RETRY = "retry";

    private KnowledgeJobType() { }
}
