package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowInvocationCommand;
import com.aether.workflow.mapper.AgentWorkflowInvocationCommandMapper;
import com.aether.workflow.service.AgentWorkflowInvocationCommandService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowInvocationCommandServiceImpl
        extends ServiceImpl<AgentWorkflowInvocationCommandMapper, AgentWorkflowInvocationCommand>
        implements AgentWorkflowInvocationCommandService {
    @Override
    public AgentWorkflowInvocationCommand findByOperation(String invocationId, String commandType, String operationKey) {
        if (StringUtils.isAnyBlank(invocationId, commandType, operationKey)) return null;
        return getOne(Wrappers.lambdaQuery(AgentWorkflowInvocationCommand.class)
                .eq(AgentWorkflowInvocationCommand::getInvocationId, invocationId)
                .eq(AgentWorkflowInvocationCommand::getCommandType, commandType)
                .eq(AgentWorkflowInvocationCommand::getOperationKey, operationKey)
                .eq(AgentWorkflowInvocationCommand::getDeleted, false)
                .last("LIMIT 1"), false);
    }
}
