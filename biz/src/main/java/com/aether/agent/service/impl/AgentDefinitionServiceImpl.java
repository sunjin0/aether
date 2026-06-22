package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.mapper.AgentDefinitionMapper;
import com.aether.agent.service.AgentDefinitionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * Agent定义 Service 实现
 */
@Service
public class AgentDefinitionServiceImpl extends ServiceImpl<AgentDefinitionMapper, AgentDefinition> implements AgentDefinitionService {
}
