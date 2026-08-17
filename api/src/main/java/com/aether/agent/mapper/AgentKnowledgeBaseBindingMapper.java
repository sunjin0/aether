package com.aether.agent.mapper;

import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供智能体知识库BaseBinding映的数据访问能力。
 */
@Mapper
public interface AgentKnowledgeBaseBindingMapper extends BaseMapper<AgentKnowledgeBaseBinding> {
}
