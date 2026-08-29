package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowSubflowLink;
import com.aether.workflow.mapper.AgentWorkflowSubflowLinkMapper;
import com.aether.workflow.service.AgentWorkflowSubflowLinkService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowSubflowLinkServiceImpl
        extends ServiceImpl<AgentWorkflowSubflowLinkMapper, AgentWorkflowSubflowLink>
        implements AgentWorkflowSubflowLinkService {
}
