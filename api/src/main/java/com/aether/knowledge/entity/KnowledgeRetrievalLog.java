package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** One retrieval candidate (or one no-match event) for RAG quality analysis. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_log")
public class KnowledgeRetrievalLog extends BaseEntity {
    private String agentDefinitionId;
    private String conversationId;
    private String messageId;
    /** SHA-256 of normalized query; raw user questions are intentionally not persisted. */
    private String queryHash;
    private String knowledgeBaseId;
    private String documentId;
    private String chunkId;
    private Double similarity;
    private Double retrievalScore;
    private Boolean cited;
    /** MATCHED or NO_MATCH. */
    private String outcome;
    private Long retrievedAt;
}
