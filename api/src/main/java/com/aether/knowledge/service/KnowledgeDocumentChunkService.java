package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 文档分块 Service 接口。
 */
public interface KnowledgeDocumentChunkService extends IService<KnowledgeDocumentChunk> {

    /**
     * 处理searchSimilarChunks。
     */
    List<KnowledgeDocumentChunk> searchSimilarChunks(List<String> knowledgeBaseIds, String embedding, int limit);

    /**
     * 处理searchLexicalChunks。
     */
    List<KnowledgeDocumentChunk> searchLexicalChunks(List<String> knowledgeBaseIds, String query, int limit);

    /**
     * Loads the chunks surrounding a matched chunk from the same indexed
     * document version.  These chunks are used as answer context, not as
     * independent similarity matches.
     */
    List<KnowledgeDocumentChunk> findNeighborChunks(String documentVersionId, int chunkIndex, int radius);

    /**
     * 保存VectorChunk。
     */
    boolean saveVectorChunk(KnowledgeDocumentChunk chunk);
}
