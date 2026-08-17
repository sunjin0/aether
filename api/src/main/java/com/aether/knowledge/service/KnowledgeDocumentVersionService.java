package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aether.knowledge.entity.KnowledgeDocument;

/**
 * 定义知识库文档Version业务服务契约。
 */
public interface KnowledgeDocumentVersionService extends IService<KnowledgeDocumentVersion> {
    /**
     * 创建下一个Version。
     */
    KnowledgeDocumentVersion createNextVersion(KnowledgeDocument document);
}
