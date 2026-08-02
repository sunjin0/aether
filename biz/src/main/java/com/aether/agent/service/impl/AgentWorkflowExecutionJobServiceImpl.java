package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentWorkflowExecutionJob;
import com.aether.agent.mapper.AgentWorkflowExecutionJobMapper;
import com.aether.agent.service.AgentWorkflowExecutionJobService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowExecutionJobServiceImpl
        extends ServiceImpl<AgentWorkflowExecutionJobMapper, AgentWorkflowExecutionJob>
        implements AgentWorkflowExecutionJobService { }
