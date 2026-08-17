package com.aether.knowledge.vo;

import lombok.Data;

/**
 * 表示知识库Ai审核Issue处理VO。
 */
@Data
public class KnowledgeAiReviewIssueHandleVo {
    /**
     * rejected/manually_fixed/ignored
     */
    private String status;
    private String comment;
}
