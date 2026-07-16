package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档分块 Mapper 接口。
 */
@Mapper
public interface KnowledgeDocumentChunkMapper extends BaseMapper<KnowledgeDocumentChunk> {

    @Select("SELECT id, knowledge_base_id, document_id, chunk_index, content, token_count, " +
            "embedding::text AS embedding, created_at, updated_at, sort_num, deleted, state " +
            "FROM knowledge_document_chunk " +
            "WHERE deleted = FALSE AND knowledge_base_id IN (${knowledgeBaseIds}) " +
            "ORDER BY embedding <=> CAST(#{embedding} AS vector) " +
            "LIMIT #{limit}")
    List<KnowledgeDocumentChunk> selectSimilarChunks(@Param("knowledgeBaseIds") String knowledgeBaseIds,
                                                 @Param("embedding") String embedding,
                                                 @Param("limit") int limit);

    @Insert("INSERT INTO knowledge_document_chunk " +
            "(id, knowledge_base_id, document_id, chunk_index, content, token_count, embedding, created_at, updated_at, sort_num, deleted, state) " +
            "VALUES " +
            "(#{chunk.id}, #{chunk.knowledgeBaseId}, #{chunk.documentId}, #{chunk.chunkIndex}, #{chunk.content}, #{chunk.tokenCount}, " +
            "CAST(#{chunk.embedding} AS vector), #{chunk.createdAt}, #{chunk.updatedAt}, #{chunk.sortNum}, #{chunk.deleted}, #{chunk.state})")
    int insertVectorChunk(@Param("chunk") KnowledgeDocumentChunk chunk);
}
