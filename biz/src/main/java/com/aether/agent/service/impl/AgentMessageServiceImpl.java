package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.mapper.AgentMessageMapper;
import com.aether.agent.service.AgentMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 消息 Service 实现
 */
@Service
public class AgentMessageServiceImpl extends ServiceImpl<AgentMessageMapper, AgentMessage> implements AgentMessageService {
}
