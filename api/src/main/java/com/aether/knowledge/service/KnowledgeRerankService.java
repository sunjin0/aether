package com.aether.knowledge.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;

import java.util.List;

/**
 * Reranks retrieved candidates through a provider that exposes a /v1/rerank-compatible endpoint.
 */
public interface KnowledgeRerankService {

    /**
     * 处理rerank。
     */
    List<KnowledgeDocumentChunk> rerank(ModelProvider provider, String model, String query,
                                        List<KnowledgeDocumentChunk> candidates, int topN);
}
