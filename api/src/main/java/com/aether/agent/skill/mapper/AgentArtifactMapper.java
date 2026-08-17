package com.aether.agent.skill.mapper;

import com.aether.agent.skill.entity.AgentArtifact;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体Artifact映的数据访问能力。
 */
@Mapper
public interface AgentArtifactMapper extends BaseMapper<AgentArtifact> {
}
