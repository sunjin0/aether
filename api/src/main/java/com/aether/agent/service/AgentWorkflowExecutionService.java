package com.aether.agent.service;

import com.aether.agent.dto.AgentWorkflowInteractionDto;
import com.aether.agent.entity.AgentWorkflowInstance;
import com.aether.agent.vo.AgentWorkflowInstanceVo;
import java.util.Map;

/** 工作流实例状态机。 */
public interface AgentWorkflowExecutionService {
    AgentWorkflowInstance start(String workflowId, Map<String, Object> variables, String userId);
    AgentWorkflowInstanceVo detail(String instanceId, String userId);
    void answer(String instanceId, AgentWorkflowInteractionDto dto, String userId);
    void retry(String instanceId, String userId);
    void terminate(String instanceId, String userId);
    /** 运行中更新流程共享状态中的开始变量字段。 */
    void updateVariables(String instanceId, Map<String, Object> variables, String userId);
}
