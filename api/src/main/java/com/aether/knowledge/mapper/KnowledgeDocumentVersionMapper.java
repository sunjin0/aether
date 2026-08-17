package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供知识库文档Version映的数据访问能力。
 */
@Mapper
public interface KnowledgeDocumentVersionMapper extends BaseMapper<KnowledgeDocumentVersion> {
}
