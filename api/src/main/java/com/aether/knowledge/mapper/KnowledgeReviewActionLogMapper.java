package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供知识库审核ActionLog映的数据访问能力。
 */
@Mapper
public interface KnowledgeReviewActionLogMapper extends BaseMapper<KnowledgeReviewActionLog> {
}
