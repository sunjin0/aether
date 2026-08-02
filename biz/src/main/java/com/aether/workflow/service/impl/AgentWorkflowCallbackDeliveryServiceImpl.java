package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.mapper.AgentWorkflowCallbackDeliveryMapper;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowCallbackDeliveryServiceImpl
        extends ServiceImpl<AgentWorkflowCallbackDeliveryMapper, AgentWorkflowCallbackDelivery>
        implements AgentWorkflowCallbackDeliveryService { }
