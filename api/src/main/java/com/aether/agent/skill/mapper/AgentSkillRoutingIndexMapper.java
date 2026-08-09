package com.aether.agent.skill.mapper;

import com.aether.agent.skill.entity.AgentSkillRoutingIndex;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AgentSkillRoutingIndexMapper extends BaseMapper<AgentSkillRoutingIndex> {
    @Select("<script>SELECT id, skill_version_id, content_hash, embedding_provider_id, embedding_model, embedding::text AS embedding, 1 - (embedding &lt;=&gt; CAST(#{embedding} AS vector)) AS vector_score, index_status, failure_reason, indexed_at, created_at, updated_at, sort_num, deleted, state FROM agent_skill_routing_index WHERE deleted = FALSE AND index_status = 1 AND skill_version_id IN <foreach collection='versionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY embedding &lt;=&gt; CAST(#{embedding} AS vector) LIMIT #{limit}</script>")
    List<AgentSkillRoutingIndex> findSimilar(@Param("versionIds") List<String> versionIds, @Param("embedding") String embedding, @Param("limit") int limit);
}
