package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.mapper.AgentMcpServerMapper;
import com.aether.agent.service.AgentMcpServerService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MCP服务 Service 实现
 */
@Service
public class AgentMcpServerServiceImpl extends ServiceImpl<AgentMcpServerMapper, AgentMcpServer> implements AgentMcpServerService {
}
