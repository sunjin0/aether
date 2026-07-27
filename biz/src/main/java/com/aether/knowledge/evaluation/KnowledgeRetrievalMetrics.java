package com.aether.knowledge.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 离线计算带标注问题集的 RAG 检索质量指标。 */
public final class KnowledgeRetrievalMetrics {
    private KnowledgeRetrievalMetrics() {
    }

    /**
     * 根据期望分块和实际召回顺序计算 Recall@K、MRR、nDCG 及引用指标。
     * expectedChunkIds 为空时按无明确目标处理，避免无目标问题影响正常统计。
     */
    public static Result evaluate(Set<String> expectedChunkIds, List<String> retrievedChunkIds,
                                  Set<String> citedChunkIds, boolean answerGrounded) {
        Set<String> expected = expectedChunkIds == null ? new HashSet<String>() : expectedChunkIds;
        Set<String> cited = citedChunkIds == null ? new HashSet<String>() : citedChunkIds;
        int firstRelevantRank = 0;
        int relevantRetrieved = 0;
        double dcg = 0D;
        if (retrievedChunkIds != null) {
            for (int i = 0; i < retrievedChunkIds.size(); i++) {
                if (expected.contains(retrievedChunkIds.get(i))) {
                    relevantRetrieved++;
                    if (firstRelevantRank == 0) firstRelevantRank = i + 1;
                    dcg += 1D / log2(i + 2D);
                }
            }
        }
        double idealDcg = 0D;
        int idealCount = Math.min(expected.size(), retrievedChunkIds == null ? 0 : retrievedChunkIds.size());
        for (int i = 0; i < idealCount; i++) idealDcg += 1D / log2(i + 2D);
        int citedRelevant = 0;
        for (String chunkId : cited) if (expected.contains(chunkId)) citedRelevant++;
        Result result = new Result();
        result.recallAtK = expected.isEmpty() ? 1D : (double) relevantRetrieved / expected.size();
        result.mrr = firstRelevantRank == 0 ? 0D : 1D / firstRelevantRank;
        result.ndcg = idealDcg == 0D ? 1D : dcg / idealDcg;
        result.citationPrecision = cited.isEmpty() ? 0D : (double) citedRelevant / cited.size();
        result.citationRecall = expected.isEmpty() ? 1D : (double) citedRelevant / expected.size();
        result.grounded = answerGrounded;
        return result;
    }

    /** 计算 nDCG 使用的以 2 为底的对数。 */
    private static double log2(double value) { return Math.log(value) / Math.log(2D); }

    /** 单条问题的检索与引用指标结果。 */
    public static class Result {
        private double recallAtK;
        private double mrr;
        private double ndcg;
        private double citationPrecision;
        private double citationRecall;
        private boolean grounded;
        public double getRecallAtK() { return recallAtK; }
        public double getMrr() { return mrr; }
        public double getNdcg() { return ndcg; }
        public double getCitationPrecision() { return citationPrecision; }
        public double getCitationRecall() { return citationRecall; }
        public boolean isGrounded() { return grounded; }
    }
}
