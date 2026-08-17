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

    /**
     * 处理selectSimilarChunks。
     */
    @Select("<script>SELECT chunk.id, chunk.knowledge_base_id, chunk.document_id, chunk.document_version_id, chunk.chunk_index, chunk.content, chunk.token_count, " +
            "chunk.page_no, chunk.section_path, chunk.content_hash, chunk.metadata, chunk.reference_count, chunk.last_referenced_at, " +
            "chunk.embedding::text AS embedding, 1 - (chunk.embedding <![CDATA[<=>]]> CAST(#{embedding} AS vector)) AS similarity, " +
            "chunk.created_at, chunk.updated_at, chunk.sort_num, chunk.deleted, chunk.state " +
            "FROM knowledge_document_chunk chunk " +
            "JOIN knowledge_document document ON document.id = chunk.document_id " +
            "JOIN knowledge_document_version version ON version.id = chunk.document_version_id " +
            "WHERE chunk.deleted = FALSE AND document.deleted = FALSE AND version.deleted = FALSE " +
            "AND version.index_status = 2 AND version.version_no = document.current_version_no " +
            "<if test='knowledgeBaseIds != null and knowledgeBaseIds.size() > 0'>" +
            "AND chunk.knowledge_base_id IN " +
            "<foreach collection='knowledgeBaseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>" +
            "ORDER BY chunk.embedding <![CDATA[<=>]]> CAST(#{embedding} AS vector) " +
            "LIMIT #{limit}</script>")
    List<KnowledgeDocumentChunk> selectSimilarChunks(@Param("knowledgeBaseIds") List<String> knowledgeBaseIds,
                                                     @Param("embedding") String embedding,
                                                     @Param("limit") int limit);

    /**
     * 处理selectLexicalChunks。
     */
    @Select("<script>SELECT chunk.id, chunk.knowledge_base_id, chunk.document_id, chunk.document_version_id, chunk.chunk_index, chunk.content, chunk.token_count, " +
            "chunk.page_no, chunk.section_path, chunk.content_hash, chunk.metadata, chunk.reference_count, chunk.last_referenced_at, " +
            "chunk.embedding::text AS embedding, " +
            "ts_rank_cd(to_tsvector('simple', chunk.content), plainto_tsquery('simple', #{query})) AS lexical_score, " +
            "chunk.created_at, chunk.updated_at, chunk.sort_num, chunk.deleted, chunk.state " +
            "FROM knowledge_document_chunk chunk " +
            "JOIN knowledge_document document ON document.id = chunk.document_id " +
            "JOIN knowledge_document_version version ON version.id = chunk.document_version_id " +
            "WHERE chunk.deleted = FALSE AND document.deleted = FALSE AND version.deleted = FALSE " +
            "AND version.index_status = 2 AND version.version_no = document.current_version_no " +
            "AND to_tsvector('simple', chunk.content) @@ plainto_tsquery('simple', #{query}) " +
            "<if test='knowledgeBaseIds != null and knowledgeBaseIds.size() > 0'>" +
            "AND chunk.knowledge_base_id IN " +
            "<foreach collection='knowledgeBaseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "ORDER BY lexical_score DESC, chunk.updated_at DESC LIMIT #{limit}</script>")
    List<KnowledgeDocumentChunk> selectLexicalChunks(@Param("knowledgeBaseIds") List<String> knowledgeBaseIds,
                                                     @Param("query") String query,
                                                     @Param("limit") int limit);

    /**
     * 处理selectNeighborChunks。
     */
    @Select("SELECT id, knowledge_base_id, document_id, document_version_id, chunk_index, content, token_count, " +
            "page_no, section_path, content_hash, metadata, reference_count, last_referenced_at, " +
            "created_at, updated_at, sort_num, deleted, state " +
            "FROM knowledge_document_chunk " +
            "WHERE deleted = FALSE AND document_version_id = #{documentVersionId} " +
            "AND chunk_index BETWEEN #{startIndex} AND #{endIndex} ORDER BY chunk_index")
    List<KnowledgeDocumentChunk> selectNeighborChunks(@Param("documentVersionId") String documentVersionId,
                                                      @Param("startIndex") int startIndex,
                                                      @Param("endIndex") int endIndex);

    /**
     * 处理insertVectorChunk。
     */
    @Insert("INSERT INTO knowledge_document_chunk " +
            "(id, knowledge_base_id, document_id, document_version_id, chunk_index, content, token_count, embedding, page_no, section_path, content_hash, metadata, reference_count, last_referenced_at, created_at, updated_at, sort_num, deleted, state) " +
            "VALUES " +
            "(#{chunk.id}, #{chunk.knowledgeBaseId}, #{chunk.documentId}, #{chunk.documentVersionId}, #{chunk.chunkIndex}, #{chunk.content}, #{chunk.tokenCount}, " +
            "CAST(#{chunk.embedding} AS vector), #{chunk.pageNo}, #{chunk.sectionPath}, #{chunk.contentHash}, #{chunk.metadata}, #{chunk.referenceCount}, #{chunk.lastReferencedAt}, " +
            "#{chunk.createdAt}, #{chunk.updatedAt}, #{chunk.sortNum}, #{chunk.deleted}, #{chunk.state})")
    int insertVectorChunk(@Param("chunk") KnowledgeDocumentChunk chunk);
}
