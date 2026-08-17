package com.aether.agent.mapper;

import com.aether.agent.entity.AgentRunPlanStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体运行PlanStep映的数据访问能力。
 */
@Mapper
public interface AgentRunPlanStepMapper extends BaseMapper<AgentRunPlanStep> {
}
