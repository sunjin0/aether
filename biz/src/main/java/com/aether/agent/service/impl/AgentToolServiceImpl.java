package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.mapper.AgentToolMapper;
import com.aether.agent.service.AgentToolService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 工具 Service 实现
 */
@Service
public class AgentToolServiceImpl extends ServiceImpl<AgentToolMapper, AgentTool> implements AgentToolService {
}
