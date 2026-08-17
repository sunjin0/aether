package com.aether.agent.mapper;

import com.aether.agent.entity.AgentSessionMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体会话Memory映的数据访问能力。
 */
@Mapper
public interface AgentSessionMemoryMapper extends BaseMapper<AgentSessionMemory> {
}
