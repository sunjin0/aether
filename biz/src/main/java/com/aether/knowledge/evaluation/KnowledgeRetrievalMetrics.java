package com.aether.knowledge.evaluation;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 离线计算带标注问题集的 RAG 检索质量指标。
 */
public final class KnowledgeRetrievalMetrics {
    /**
     * 创建 {@code KnowledgeRetrievalMetrics} 实例。
     */
    private KnowledgeRetrievalMetrics() {
    }

    /**
     * 根据期望分块和实际召回顺序计算 Recall@K、MRR、nDCG 及引用指标。
     * expectedChunkIds 为空时按无明确目标处理，避免无目标问题影响正常统计。
     */
    public static Result evaluate(Set<String> expectedChunkIds, List<String> retrievedChunkIds,
                                  Set<String> citedChunkIds, boolean answerGrounded) {
        return evaluate(expectedChunkIds, retrievedChunkIds, citedChunkIds, answerGrounded, "CHUNK");
    }

    /**
     * DOCUMENT 标注表示任一目标分块命中即可，SECTION 和 CHUNK 标注计算分块覆盖率。
     * 重复召回不应同时提高 Recall 或 nDCG。
     */
    public static Result evaluate(Set<String> expectedChunkIds, List<String> retrievedChunkIds,
                                  Set<String> citedChunkIds, boolean answerGrounded, String targetType) {
        Set<String> expected = expectedChunkIds == null ? new HashSet<String>() : new HashSet<String>(expectedChunkIds);
        Set<String> cited = citedChunkIds == null ? new HashSet<String>() : citedChunkIds;
        List<String> retrieved = distinct(retrievedChunkIds);
        boolean documentTarget = "DOCUMENT".equalsIgnoreCase(targetType);
        int firstRelevantRank = 0;
        int relevantRetrieved = 0;
        double dcg = 0D;
        for (int i = 0; i < retrieved.size(); i++) {
            if (expected.contains(retrieved.get(i))) {
                relevantRetrieved++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                    if (documentTarget) dcg += 1D / log2(i + 2D);
                }
                if (!documentTarget) dcg += 1D / log2(i + 2D);
            }
        }
        double idealDcg = 0D;
        int idealCount = Math.min(documentTarget ? Math.min(expected.size(), 1) : expected.size(), retrieved.size());
        for (int i = 0; i < idealCount; i++) idealDcg += 1D / log2(i + 2D);
        int citedRelevant = 0;
        for (String chunkId : cited) if (expected.contains(chunkId)) citedRelevant++;
        Result result = new Result();
        result.recallAtK = expected.isEmpty() ? 1D : documentTarget ? (relevantRetrieved > 0 ? 1D : 0D) : (double) relevantRetrieved / expected.size();
        result.mrr = firstRelevantRank == 0 ? 0D : 1D / firstRelevantRank;
        result.ndcg = idealDcg == 0D ? 1D : dcg / idealDcg;
        result.citationPrecision = cited.isEmpty() ? 0D : (double) citedRelevant / cited.size();
        result.citationRecall = expected.isEmpty() ? 1D : (double) citedRelevant / expected.size();
        result.grounded = answerGrounded;
        return result;
    }

    /**
     * 处理distinct。
     */
    private static List<String> distinct(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) return new ArrayList<String>();
        List<String> result = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String chunkId : chunkIds) if (chunkId != null && seen.add(chunkId)) result.add(chunkId);
        return result;
    }

    /**
     * 计算 nDCG 使用的以 2 为底的对数。
     */
    private static double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    /**
     * 单条问题的检索与引用指标结果。
     */
    public static class Result {
        private double recallAtK;
        private double mrr;
        private double ndcg;
        private double citationPrecision;
        private double citationRecall;
        private boolean grounded;

        /**
         * 获取RecallAtK。
         */
        public double getRecallAtK() {
            return recallAtK;
        }

        /**
         * 获取Mrr。
         */
        public double getMrr() {
            return mrr;
        }

        /**
         * 获取Ndcg。
         */
        public double getNdcg() {
            return ndcg;
        }

        /**
         * 获取CitationPrecision。
         */
        public double getCitationPrecision() {
            return citationPrecision;
        }

        /**
         * 获取CitationRecall。
         */
        public double getCitationRecall() {
            return citationRecall;
        }

        /**
         * 判断是否为Grounded。
         */
        public boolean isGrounded() {
            return grounded;
        }
    }
}
