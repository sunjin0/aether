package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowJoinState;
import com.aether.workflow.mapper.AgentWorkflowJoinStateMapper;
import com.aether.workflow.service.AgentWorkflowJoinStateService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowJoinStateServiceImpl extends ServiceImpl<AgentWorkflowJoinStateMapper, AgentWorkflowJoinState>
        implements AgentWorkflowJoinStateService {
}
