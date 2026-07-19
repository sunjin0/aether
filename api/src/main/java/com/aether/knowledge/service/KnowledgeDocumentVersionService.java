package com.aether.knowledge.service;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aether.knowledge.entity.KnowledgeDocument;
public interface KnowledgeDocumentVersionService extends IService<KnowledgeDocumentVersion> {
    KnowledgeDocumentVersion createNextVersion(KnowledgeDocument document);
}
