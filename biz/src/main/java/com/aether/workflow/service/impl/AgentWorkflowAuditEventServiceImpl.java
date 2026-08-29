package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowAuditEvent;
import com.aether.workflow.mapper.AgentWorkflowAuditEventMapper;
import com.aether.workflow.runtime.WorkflowSensitiveDataSanitizer;
import com.aether.workflow.service.AgentWorkflowAuditEventService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流审计事件只新增、不提供修改或删除业务操作。
 */
@Service
public class AgentWorkflowAuditEventServiceImpl extends ServiceImpl<AgentWorkflowAuditEventMapper, AgentWorkflowAuditEvent>
        implements AgentWorkflowAuditEventService {
    private final WorkflowSensitiveDataSanitizer sensitiveDataSanitizer;

    public AgentWorkflowAuditEventServiceImpl(WorkflowSensitiveDataSanitizer sensitiveDataSanitizer) {
        this.sensitiveDataSanitizer = sensitiveDataSanitizer;
    }

    @Override
    public void record(String instanceId, String nodeInstanceId, String eventType, String actorId, String summary, String data) {
        if (StringUtils.isBlank(instanceId) || StringUtils.isBlank(eventType)) return;
        AgentWorkflowAuditEvent event = new AgentWorkflowAuditEvent();
        event.setInstanceId(instanceId);
        event.setNodeInstanceId(nodeInstanceId);
        event.setEventType(eventType);
        event.setActorId(StringUtils.abbreviate(actorId, 128));
        event.setSummary(StringUtils.abbreviate(StringUtils.defaultString(summary), 1000));
        event.setData(sensitiveDataSanitizer.sanitizeJson(data));
        event.setOccurredAt(System.currentTimeMillis());
        save(event);
    }

    @Override
    public List<AgentWorkflowAuditEvent> listByInstanceId(String instanceId) {
        return list(Wrappers.lambdaQuery(AgentWorkflowAuditEvent.class)
                .eq(AgentWorkflowAuditEvent::getInstanceId, instanceId)
                .eq(AgentWorkflowAuditEvent::getDeleted, false)
                .orderByAsc(AgentWorkflowAuditEvent::getOccurredAt));
    }
}
