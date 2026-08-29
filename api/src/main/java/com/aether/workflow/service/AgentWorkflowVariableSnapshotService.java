package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowVariableSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentWorkflowVariableSnapshotService extends IService<AgentWorkflowVariableSnapshot> {
    void capture(String instanceId, String nodeInstanceId, String nodeId, String variables);
}
