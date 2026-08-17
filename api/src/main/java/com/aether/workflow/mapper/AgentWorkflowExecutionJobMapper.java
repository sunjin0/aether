package com.aether.workflow.mapper;

import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体工作流ExecutionJob映的数据访问能力。
 */
@Mapper
public interface AgentWorkflowExecutionJobMapper extends BaseMapper<AgentWorkflowExecutionJob> {
}
