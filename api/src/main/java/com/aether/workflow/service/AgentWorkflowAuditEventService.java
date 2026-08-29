package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowAuditEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentWorkflowAuditEventService extends IService<AgentWorkflowAuditEvent> {
    void record(String instanceId, String nodeInstanceId, String eventType, String actorId, String summary, String data);

    List<AgentWorkflowAuditEvent> listByInstanceId(String instanceId);
}
