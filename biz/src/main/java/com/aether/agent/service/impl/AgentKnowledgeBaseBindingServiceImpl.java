package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.mapper.AgentKnowledgeBaseBindingMapper;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentKnowledgeBaseBindingServiceImpl
        extends ServiceImpl<AgentKnowledgeBaseBindingMapper, AgentKnowledgeBaseBinding>
        implements AgentKnowledgeBaseBindingService {
}
