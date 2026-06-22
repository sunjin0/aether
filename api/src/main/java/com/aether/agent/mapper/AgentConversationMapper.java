package com.aether.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aether.agent.entity.AgentConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper 接口
 */
@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {
}
