package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowInvocationOutbox;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentWorkflowInvocationOutboxService extends IService<AgentWorkflowInvocationOutbox> {
    boolean enqueue(AgentWorkflowInvocationOutbox event);
    List<AgentWorkflowInvocationOutbox> claimPending(long now, int limit);
}
