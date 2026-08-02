package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.aether.workflow.mapper.AgentWorkflowExecutionJobMapper;
import com.aether.workflow.service.AgentWorkflowExecutionJobService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowExecutionJobServiceImpl
        extends ServiceImpl<AgentWorkflowExecutionJobMapper, AgentWorkflowExecutionJob>
        implements AgentWorkflowExecutionJobService { }
