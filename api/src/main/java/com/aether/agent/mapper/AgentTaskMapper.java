package com.aether.agent.mapper;

import com.aether.agent.entity.AgentTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体任务映的数据访问能力。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
