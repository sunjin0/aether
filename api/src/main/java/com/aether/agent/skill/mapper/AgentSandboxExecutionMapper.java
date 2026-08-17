package com.aether.agent.skill.mapper;

import com.aether.agent.skill.entity.AgentSandboxExecution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体SandboxExecution映的数据访问能力。
 */
@Mapper
public interface AgentSandboxExecutionMapper extends BaseMapper<AgentSandboxExecution> {
}
