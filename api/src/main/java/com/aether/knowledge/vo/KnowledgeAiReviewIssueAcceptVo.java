package com.aether.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeAiReviewIssueAcceptVo {
    /** Required draft checksum used for optimistic concurrency control. */
    private String expectedChecksum;
    /** Optional human-edited replacement, overriding the AI suggestion. */
    private String replacement;
    private String comment;
}
