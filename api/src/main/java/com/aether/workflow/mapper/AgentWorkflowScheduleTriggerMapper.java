package com.aether.workflow.mapper;

import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体工作流调度Trigger映的数据访问能力。
 */
@Mapper
public interface AgentWorkflowScheduleTriggerMapper extends BaseMapper<AgentWorkflowScheduleTrigger> {
}
