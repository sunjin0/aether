package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供知识库Ai审核Issue映的数据访问能力。
 */
@Mapper
public interface KnowledgeAiReviewIssueMapper extends BaseMapper<KnowledgeAiReviewIssue> {
}
