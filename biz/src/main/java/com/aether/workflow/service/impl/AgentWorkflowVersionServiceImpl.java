package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.mapper.AgentWorkflowVersionMapper;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现智能体工作流Version业务服务。
 */
@Service
public class AgentWorkflowVersionServiceImpl extends ServiceImpl<AgentWorkflowVersionMapper, AgentWorkflowVersion> implements AgentWorkflowVersionService {
}
