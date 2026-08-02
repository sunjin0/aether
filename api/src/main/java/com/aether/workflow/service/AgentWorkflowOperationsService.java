package com.aether.workflow.service;

import com.aether.workflow.vo.AgentWorkflowDeadLetterVo;
import com.aether.workflow.vo.AgentWorkflowOperationsMetricsVo;
import java.util.List;

public interface AgentWorkflowOperationsService {
    AgentWorkflowOperationsMetricsVo metrics();
    List<AgentWorkflowDeadLetterVo> deadLetters(int limit);
}
