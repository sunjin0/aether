package com.aether.knowledge.evaluation;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRetrievalMetricsTest {
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

    @Test
    void treatsAnyChunkFromDocumentTargetAsAHit() {
        KnowledgeRetrievalMetrics.Result result = KnowledgeRetrievalMetrics.evaluate(
                new HashSet<String>(Arrays.asList("chapter-one", "chapter-two", "chapter-three")),
                Arrays.asList("unrelated", "chapter-two"), new HashSet<String>(), false, "DOCUMENT");
        assertEquals(1D, result.getRecallAtK());
        assertEquals(0.5D, result.getMrr());
    }

    @Test
    void doesNotCountRepeatedChunksMoreThanOnce() {
        KnowledgeRetrievalMetrics.Result result = KnowledgeRetrievalMetrics.evaluate(
                new HashSet<String>(Arrays.asList("refund-policy", "refund-exception")),
                Arrays.asList("refund-policy", "refund-policy", "unrelated"), new HashSet<String>(), false, "CHUNK");
        assertEquals(0.5D, result.getRecallAtK());
        assertEquals(1D, result.getMrr());
    }
}
