package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowNodeToken;
import com.aether.workflow.mapper.AgentWorkflowNodeTokenMapper;
import com.aether.workflow.service.AgentWorkflowNodeTokenService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowNodeTokenServiceImpl extends ServiceImpl<AgentWorkflowNodeTokenMapper, AgentWorkflowNodeToken>
        implements AgentWorkflowNodeTokenService {
}
