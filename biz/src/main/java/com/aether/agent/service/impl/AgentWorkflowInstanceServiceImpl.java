package com.aether.agent.service.impl;
import com.aether.agent.entity.AgentWorkflowInstance;
import com.aether.agent.mapper.AgentWorkflowInstanceMapper;
import com.aether.agent.service.AgentWorkflowInstanceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
@Service public class AgentWorkflowInstanceServiceImpl extends ServiceImpl<AgentWorkflowInstanceMapper, AgentWorkflowInstance> implements AgentWorkflowInstanceService { }
