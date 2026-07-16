package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocument;

public interface KnowledgeDocumentIndexService {

    void reindex(KnowledgeDocument document);
}
