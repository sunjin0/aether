package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.mapper.AgentToolCallLogMapper;
import com.aether.agent.service.AgentToolCallLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 工具调用日志 Service 实现
 */
@Service
public class AgentToolCallLogServiceImpl extends ServiceImpl<AgentToolCallLogMapper, AgentToolCallLog> implements AgentToolCallLogService {
}
