package com.aether.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aether.agent.entity.AgentToolCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用日志 Mapper 接口
 */
@Mapper
public interface AgentToolCallLogMapper extends BaseMapper<AgentToolCallLog> {
}
