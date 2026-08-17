package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.mapper.AgentWorkflowCallbackDeliveryMapper;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现智能体工作流回调Delivery业务服务。
 */
@Service
public class AgentWorkflowCallbackDeliveryServiceImpl
        extends ServiceImpl<AgentWorkflowCallbackDeliveryMapper, AgentWorkflowCallbackDelivery>
        implements AgentWorkflowCallbackDeliveryService {
}
