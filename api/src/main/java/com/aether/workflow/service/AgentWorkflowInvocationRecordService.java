package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentWorkflowInvocationRecordService extends IService<AgentWorkflowInvocation> {
    AgentWorkflowInvocation findByOperation(String capabilityId, String principalId, String operationKey);
}
