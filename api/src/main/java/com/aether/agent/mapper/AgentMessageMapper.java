package com.aether.agent.mapper;

import com.aether.agent.entity.AgentMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper 接口
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}
