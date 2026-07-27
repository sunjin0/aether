package com.aether.knowledge.vo;

import lombok.Data;

import java.util.List;

/** A persisted evaluation result enriched for the administration UI. */
@Data
public class KnowledgeRetrievalEvaluationResultVo {
    private String id;
    private String evaluationCaseId;
    private String question;
    private String expectedDocumentId;
    private String expectedDocumentTitle;
    private String expectedSectionPath;
    private String status;
    private Double recallAtK;
    private Double mrr;
    private Double ndcg;
    private List<RetrievedChunk> retrievedChunks;

    @Data
    public static class RetrievedChunk {
        private String id;
        private String documentId;
        private String documentTitle;
        private String sectionPath;
        private Integer chunkIndex;
        private Integer rank;
    }
}
