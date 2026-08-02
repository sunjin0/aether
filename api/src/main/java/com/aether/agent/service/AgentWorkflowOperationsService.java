package com.aether.agent.service;

import com.aether.agent.vo.AgentWorkflowDeadLetterVo;
import com.aether.agent.vo.AgentWorkflowOperationsMetricsVo;
import java.util.List;

public interface AgentWorkflowOperationsService {
    AgentWorkflowOperationsMetricsVo metrics();
    List<AgentWorkflowDeadLetterVo> deadLetters(int limit);
}
