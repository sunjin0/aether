package com.aether.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aether.agent.entity.AgentDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent定义 Mapper 接口
 */
@Mapper
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinition> {
}
