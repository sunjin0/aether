package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.mapper.AgentWorkflowInvocationMapper;
import com.aether.workflow.service.AgentWorkflowInvocationRecordService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowInvocationRecordServiceImpl extends ServiceImpl<AgentWorkflowInvocationMapper, AgentWorkflowInvocation>
        implements AgentWorkflowInvocationRecordService {
    @Override
    public AgentWorkflowInvocation findByOperation(String capabilityId, String principalId, String operationKey) {
        return getOne(Wrappers.lambdaQuery(AgentWorkflowInvocation.class)
                .eq(AgentWorkflowInvocation::getCapabilityId, capabilityId)
                .eq(AgentWorkflowInvocation::getPrincipalId, principalId)
                .eq(AgentWorkflowInvocation::getOperationKey, operationKey)
                .eq(AgentWorkflowInvocation::getDeleted, false)
                .last("LIMIT 1"), false);
    }
}
