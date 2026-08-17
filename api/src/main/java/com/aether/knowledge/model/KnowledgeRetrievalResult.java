package com.aether.knowledge.model;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 表示知识库Retrieval结果。
 */
@Data
public class KnowledgeRetrievalResult {
    /**
     * Whether this request reached at least one enabled knowledge base with a usable embedding provider.
     */
    private boolean retrievalAttempted;
    /**
     * Whether every attempted embedding provider failed before producing a retrieval response.
     */
    private boolean retrievalFailed;
    /**
     * When enabled, the answer must be grounded entirely in retrieved knowledge.
     */
    private boolean strictGrounding;
    private String context;
    private List<KnowledgeDocumentChunk> chunks = Collections.emptyList();
}
