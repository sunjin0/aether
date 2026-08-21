package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentConversationSummary;
import com.aether.agent.mapper.AgentConversationSummaryMapper;
import com.aether.agent.service.AgentConversationSummaryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentConversationSummaryServiceImpl
        extends ServiceImpl<AgentConversationSummaryMapper, AgentConversationSummary>
        implements AgentConversationSummaryService {
}

