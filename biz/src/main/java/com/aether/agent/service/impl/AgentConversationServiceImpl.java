package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.mapper.AgentConversationMapper;
import com.aether.agent.service.AgentConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 会话 Service 实现
 */
@Service
public class AgentConversationServiceImpl extends ServiceImpl<AgentConversationMapper, AgentConversation> implements AgentConversationService {
}
