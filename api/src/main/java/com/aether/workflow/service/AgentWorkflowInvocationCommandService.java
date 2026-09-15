package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowInvocationCommand;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentWorkflowInvocationCommandService extends IService<AgentWorkflowInvocationCommand> {
    AgentWorkflowInvocationCommand findByOperation(String invocationId, String commandType, String operationKey);
}
