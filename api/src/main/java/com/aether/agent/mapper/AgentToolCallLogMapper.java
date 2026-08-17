package com.aether.agent.mapper;

import com.aether.agent.entity.AgentToolCallLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用日志 Mapper 接口
 */
@Mapper
public interface AgentToolCallLogMapper extends BaseMapper<AgentToolCallLog> {
}
