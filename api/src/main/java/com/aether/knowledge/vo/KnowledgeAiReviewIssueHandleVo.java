package com.aether.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeAiReviewIssueHandleVo {
    /** rejected/manually_fixed/ignored */
    private String status;
    private String comment;
}
