package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeReviewTaskMapper extends BaseMapper<KnowledgeReviewTask> {
    @Update("UPDATE knowledge_review_task SET status = 'claimed', reviewer_id = #{reviewerId}, " +
            "claimed_at = #{now}, updated_at = #{now} WHERE id = #{id} AND status = 'pending' AND deleted = FALSE")
    int claim(@Param("id") String id, @Param("reviewerId") String reviewerId, @Param("now") long now);
}
