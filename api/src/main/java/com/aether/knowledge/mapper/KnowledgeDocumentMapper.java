package com.aether.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aether.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档 Mapper 接口（V0.7预留）
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT * FROM knowledge_document WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    KnowledgeDocument selectActiveForUpdate(@Param("id") String id);
}
