package com.aether.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aether.agent.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper 接口
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}
