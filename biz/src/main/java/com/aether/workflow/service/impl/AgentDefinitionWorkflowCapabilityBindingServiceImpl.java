package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentDefinitionWorkflowCapabilityBinding;
import com.aether.workflow.mapper.AgentDefinitionWorkflowCapabilityBindingMapper;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentDefinitionWorkflowCapabilityBindingServiceImpl
        extends ServiceImpl<AgentDefinitionWorkflowCapabilityBindingMapper, AgentDefinitionWorkflowCapabilityBinding>
        implements AgentDefinitionWorkflowCapabilityBindingService {
}
