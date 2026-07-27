package com.aether.knowledge.mapper;
import com.aether.knowledge.entity.KnowledgeReferenceLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeReferenceLogMapper extends BaseMapper<KnowledgeReferenceLog> {

    @Update("UPDATE knowledge_document_chunk SET reference_count = COALESCE(reference_count, 0) + 1, "
            + "last_referenced_at = #{referencedAt} WHERE id = #{chunkId} AND deleted = FALSE")
    int incrementChunkReference(@Param("chunkId") String chunkId, @Param("referencedAt") long referencedAt);

    @Update("UPDATE knowledge_document SET reference_count = COALESCE(reference_count, 0) + 1, "
            + "last_referenced_at = #{referencedAt} WHERE id = #{documentId} AND deleted = FALSE")
    int incrementDocumentReference(@Param("documentId") String documentId, @Param("referencedAt") long referencedAt);

    @Update("UPDATE knowledge_base SET reference_count = COALESCE(reference_count, 0) + 1, "
            + "last_referenced_at = #{referencedAt} WHERE id = #{knowledgeBaseId} AND deleted = FALSE")
    int incrementKnowledgeBaseReference(@Param("knowledgeBaseId") String knowledgeBaseId,
                                        @Param("referencedAt") long referencedAt);
}
