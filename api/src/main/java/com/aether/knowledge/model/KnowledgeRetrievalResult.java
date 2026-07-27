package com.aether.knowledge.model;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class KnowledgeRetrievalResult {
    /** Whether this request reached at least one enabled knowledge base with a usable embedding provider. */
    private boolean retrievalAttempted;
    /** When enabled, the answer must be grounded entirely in retrieved knowledge. */
    private boolean strictGrounding;
    private String context;
    private List<KnowledgeDocumentChunk> chunks = Collections.emptyList();
}
