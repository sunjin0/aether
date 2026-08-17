package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeRetrievalLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供知识库RetrievalLog映的数据访问能力。
 */
@Mapper
public interface KnowledgeRetrievalLogMapper extends BaseMapper<KnowledgeRetrievalLog> {
}
