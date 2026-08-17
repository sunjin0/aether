package com.aether.agent.mapper;

import com.aether.agent.entity.AgentRunStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体运行Step映的数据访问能力。
 */
@Mapper
public interface AgentRunStepMapper extends BaseMapper<AgentRunStep> {
}
