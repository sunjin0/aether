package com.aether.knowledge.model;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class KnowledgeRetrievalResult {
    private String context;
    private List<KnowledgeDocumentChunk> chunks = Collections.emptyList();
}
