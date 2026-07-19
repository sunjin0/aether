package com.aether.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeReviewTaskQueryVo {
    private String knowledgeBaseId;
    private String documentId;
    private String status;
    /** all/available/submittedByMe/reviewedByMe */
    private String view;
    private Long current = 1L;
    private Long pageSize = 20L;
}
