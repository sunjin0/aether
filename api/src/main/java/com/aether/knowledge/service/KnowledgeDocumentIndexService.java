package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;

public interface KnowledgeDocumentIndexService {

    void reindex(KnowledgeDocument document);

    /**
     * 为指定文档版本建立向量索引。
     * 分块属于该版本；重试时仅清理并重建该版本的分块，不影响已发布历史版本。
     */
    void reindex(KnowledgeDocument document, KnowledgeDocumentVersion version);

    String queueReindex(KnowledgeDocument document, KnowledgeDocumentVersion version, String jobType);
}
