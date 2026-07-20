package com.aether.knowledge.vo;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeAiReviewIssueBatchAcceptVo {
    private List<String> issueIds;
    private String expectedChecksum;
    private String comment;
}
