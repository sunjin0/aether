package com.aether.knowledge.mapper;

import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.model.KnowledgeIndexJobStatus;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 提供知识库索引Job映的数据访问能力。
 */
@Mapper
public interface KnowledgeIndexJobMapper extends BaseMapper<KnowledgeIndexJob> {

    /**
     * 处理claimPending。
     */
    @Update("UPDATE knowledge_index_job SET status = '" + KnowledgeIndexJobStatus.RUNNING
            + "', started_at = #{startedAt}, updated_at = #{startedAt} WHERE id = #{id} "
            + "AND status = '" + KnowledgeIndexJobStatus.PENDING + "' AND deleted = FALSE")
    int claimPending(@Param("id") String id, @Param("startedAt") long startedAt);
}
