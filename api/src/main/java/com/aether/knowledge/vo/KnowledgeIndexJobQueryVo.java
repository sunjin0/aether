package com.aether.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeIndexJobQueryVo {
    private String knowledgeBaseId;
    private String documentId;
    private String jobType;
    private String status;
    private Long current = 1L;
    private Long pageSize = 20L;
}
