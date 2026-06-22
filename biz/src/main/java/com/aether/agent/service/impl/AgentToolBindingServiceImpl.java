package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.mapper.AgentToolBindingMapper;
import com.aether.agent.service.AgentToolBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 工具绑定 Service 实现
 */
@Service
public class AgentToolBindingServiceImpl extends ServiceImpl<AgentToolBindingMapper, AgentToolBinding> implements AgentToolBindingService {
}
