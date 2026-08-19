package com.aether.agent.mapper;

import com.aether.agent.entity.AgentToolRoutingIndex;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 提供智能体工具路由索引的数据访问能力。
 */
@Mapper
public interface AgentToolRoutingIndexMapper extends BaseMapper<AgentToolRoutingIndex> {
    /**
     * 查找Similar。
     */
    @Select("<script>SELECT id, tool_id, content_hash, embedding_provider_id, embedding_model, embedding::text AS embedding, 1 - (embedding &lt;=&gt; CAST(#{embedding} AS vector)) AS vector_score, index_status, failure_reason, indexed_at, created_at, updated_at, sort_num, deleted, state FROM agent_tool_routing_index WHERE deleted = FALSE AND index_status = 1 AND tool_id IN <foreach collection='toolIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY embedding &lt;=&gt; CAST(#{embedding} AS vector) LIMIT #{limit}</script>")
    List<AgentToolRoutingIndex> findSimilar(@Param("toolIds") List<String> toolIds, @Param("embedding") String embedding, @Param("limit") int limit);

    /**
     * 处理insertVector索引。
     */
    @Insert("INSERT INTO agent_tool_routing_index (id, tool_id, content_hash, embedding_provider_id, embedding_model, embedding, index_status, failure_reason, indexed_at, created_at, updated_at, sort_num, deleted, state) " +
            "VALUES (#{item.id}, #{item.toolId}, #{item.contentHash}, #{item.embeddingProviderId}, #{item.embeddingModel}, CAST(#{item.embedding} AS vector), #{item.indexStatus}, #{item.failureReason}, #{item.indexedAt}, #{item.createdAt}, #{item.updatedAt}, #{item.sortNum}, #{item.deleted}, #{item.state})")
    int insertVectorIndex(@Param("item") AgentToolRoutingIndex item);

    /**
     * 更新Vector索引。
     */
    @Update("UPDATE agent_tool_routing_index SET content_hash=#{item.contentHash}, embedding_provider_id=#{item.embeddingProviderId}, embedding_model=#{item.embeddingModel}, embedding=CAST(#{item.embedding} AS vector), index_status=#{item.indexStatus}, failure_reason=#{item.failureReason}, indexed_at=#{item.indexedAt}, updated_at=#{item.updatedAt} WHERE id=#{item.id}")
    int updateVectorIndex(@Param("item") AgentToolRoutingIndex item);
}
