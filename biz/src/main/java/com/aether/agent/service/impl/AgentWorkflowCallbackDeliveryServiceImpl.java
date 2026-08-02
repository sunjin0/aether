package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentWorkflowCallbackDelivery;
import com.aether.agent.mapper.AgentWorkflowCallbackDeliveryMapper;
import com.aether.agent.service.AgentWorkflowCallbackDeliveryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowCallbackDeliveryServiceImpl
        extends ServiceImpl<AgentWorkflowCallbackDeliveryMapper, AgentWorkflowCallbackDelivery>
        implements AgentWorkflowCallbackDeliveryService { }
