package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.mapper.AgentRunMapper;
import com.aether.agent.service.AgentRunService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 运行记录 Service 实现
 */
@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRun> implements AgentRunService {
}
