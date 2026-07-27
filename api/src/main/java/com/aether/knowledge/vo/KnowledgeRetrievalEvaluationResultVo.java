package com.aether.knowledge.vo;

import lombok.Data;

import java.util.List;

/** 面向管理端的评测结果视图，包含期望来源和实际召回来源。 */
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
        /** 召回分块 ID。 */
        private String id;
        /** 分块所属文档 ID。 */
        private String documentId;
        /** 分块所属文档标题。 */
        private String documentTitle;
        /** 分块章节路径。 */
        private String sectionPath;
        /** 文档内分块序号。 */
        private Integer chunkIndex;
        /** 本次检索中的召回名次，从 1 开始。 */
        private Integer rank;
    }
}
