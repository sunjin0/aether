package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 文档分块 Service 接口。
 */
public interface KnowledgeDocumentChunkService extends IService<KnowledgeDocumentChunk> {

    List<KnowledgeDocumentChunk> searchSimilarChunks(List<String> knowledgeBaseIds, String embedding, int limit);

    boolean saveVectorChunk(KnowledgeDocumentChunk chunk);
}
