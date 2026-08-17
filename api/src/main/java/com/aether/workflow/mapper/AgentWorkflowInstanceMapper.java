package com.aether.workflow.mapper;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体工作流Instance映的数据访问能力。
 */
@Mapper
public interface AgentWorkflowInstanceMapper extends BaseMapper<AgentWorkflowInstance> {
}
