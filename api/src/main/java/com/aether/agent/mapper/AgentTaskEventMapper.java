package com.aether.agent.mapper;

import com.aether.agent.entity.AgentTaskEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体任务事件映的数据访问能力。
 */
@Mapper
public interface AgentTaskEventMapper extends BaseMapper<AgentTaskEvent> {
}
