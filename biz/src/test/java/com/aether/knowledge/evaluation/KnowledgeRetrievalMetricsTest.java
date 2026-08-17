package com.aether.knowledge.evaluation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证知识库RetrievalMetrics的行为。
 */
class KnowledgeRetrievalMetricsTest {
    /**
     * 处理calculatesRetrievalAndCitationMetricsAgainstLabelledChunks。
     */
    @Test
    void calculatesRetrievalAndCitationMetricsAgainstLabelledChunks() {
        KnowledgeRetrievalMetrics.Result result = KnowledgeRetrievalMetrics.evaluate(
                new HashSet<String>(Arrays.asList("refund-policy", "refund-exception")),
                Arrays.asList("unrelated", "refund-policy", "refund-exception"),
                new HashSet<String>(Arrays.asList("refund-policy")), true);
        assertEquals(1D, result.getRecallAtK());
        assertEquals(0.5D, result.getMrr());
        assertEquals(1D, result.getCitationPrecision());
        assertEquals(0.5D, result.getCitationRecall());
        assertTrue(result.isGrounded());
    }

    /**
     * 处理treatsAnyChunkFrom文档TargetAsAHit。
     */
    @Test
    void treatsAnyChunkFromDocumentTargetAsAHit() {
        KnowledgeRetrievalMetrics.Result result = KnowledgeRetrievalMetrics.evaluate(
                new HashSet<String>(Arrays.asList("chapter-one", "chapter-two", "chapter-three")),
                Arrays.asList("unrelated", "chapter-two"), new HashSet<String>(), false, "DOCUMENT");
        assertEquals(1D, result.getRecallAtK());
        assertEquals(0.5D, result.getMrr());
    }

    /**
     * 处理doesNot统计RepeatedChunksMoreThanOnce。
     */
    @Test
    void doesNotCountRepeatedChunksMoreThanOnce() {
        KnowledgeRetrievalMetrics.Result result = KnowledgeRetrievalMetrics.evaluate(
                new HashSet<String>(Arrays.asList("refund-policy", "refund-exception")),
                Arrays.asList("refund-policy", "refund-policy", "unrelated"), new HashSet<String>(), false, "CHUNK");
        assertEquals(0.5D, result.getRecallAtK());
        assertEquals(1D, result.getMrr());
    }
}
