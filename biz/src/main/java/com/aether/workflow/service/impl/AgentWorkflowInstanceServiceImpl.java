package com.aether.workflow.service.impl;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.mapper.AgentWorkflowInstanceMapper;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
@Service public class AgentWorkflowInstanceServiceImpl extends ServiceImpl<AgentWorkflowInstanceMapper, AgentWorkflowInstance> implements AgentWorkflowInstanceService { }
