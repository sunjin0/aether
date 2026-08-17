package com.aether.knowledge.vo;

import lombok.Data;

import java.util.List;

/**
 * 表示知识库Ai审核IssueBatchAcceptVO。
 */
@Data
public class KnowledgeAiReviewIssueBatchAcceptVo {
    private List<String> issueIds;
    private String expectedChecksum;
    private String comment;
}
