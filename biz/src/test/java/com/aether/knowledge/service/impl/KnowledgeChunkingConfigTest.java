package com.aether.knowledge.service.impl;

import com.aether.knowledge.model.KnowledgeChunkingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证知识库分片策略配置的默认值与边界。 */
class KnowledgeChunkingConfigTest {

    @Test
    void usesDefaultsForHistoricalKnowledgeBases() {
        KnowledgeChunkingConfig config = KnowledgeChunkingConfig.fromJson(null);

        assertEquals(2400, config.getMaxChars());
        assertEquals(KnowledgeChunkingConfig.STRATEGY_SEMANTIC, config.getStrategy());
        assertEquals(320, config.getOverlapChars());
        assertEquals(1400, config.getMaxTokens());
    }

    @Test
    void acceptsAndNormalizesConfiguredLimits() {
        KnowledgeChunkingConfig config = KnowledgeChunkingConfig.fromJson("{\"strategy\":\"FIXED_LENGTH\",\"maxChars\":800,\"overlapChars\":120,\"maxTokens\":400}");

        assertEquals(KnowledgeChunkingConfig.STRATEGY_FIXED_LENGTH, config.getStrategy());
        assertEquals(800, config.getMaxChars());
        assertEquals(120, config.getOverlapChars());
        assertEquals(400, config.getMaxTokens());
        assertEquals("{\"strategy\":\"FIXED_LENGTH\",\"maxChars\":800,\"overlapChars\":120,\"maxTokens\":400}", config.toJson());
    }

    @Test
    void rejectsOverlapThatWouldPreventProgress() {
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeChunkingConfig.fromJson("{\"maxChars\":800,\"overlapChars\":800}"));
    }

    @Test
    void rejectsValuesOutsideTheEmbeddingSafeRange() {
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeChunkingConfig.fromJson("{\"maxChars\":4097}"));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeChunkingConfig.fromJson("{\"maxTokens\":2049}"));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeChunkingConfig.fromJson("{\"maxChars\":1000,\"overlapChars\":501}"));
    }
}
