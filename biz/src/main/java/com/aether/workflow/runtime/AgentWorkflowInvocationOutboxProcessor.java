package com.aether.workflow.runtime;

import com.aether.agent.service.AgentTaskEventService;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.entity.AgentWorkflowInvocationOutbox;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentWorkflowInvocationOutboxService;
import com.aether.workflow.service.AgentWorkflowInvocationRecordService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将工作流实例状态变化持久化并投递到 Agent 任务事件表。
 * 内存 SSE 只负责在线刷新，本组件负责重启后的补偿与去重。
 */
@Component
public class AgentWorkflowInvocationOutboxProcessor {
    private final AgentWorkflowInvocationRecordService invocationService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowInvocationOutboxService outboxService;
    private final AgentTaskEventService taskEventService;
    private final WorkflowOutputResolver outputResolver;
    private final WorkflowSensitiveDataSanitizer sanitizer;
    private final AgentWorkflowCapabilityService capabilityService;

    public AgentWorkflowInvocationOutboxProcessor(AgentWorkflowInvocationRecordService invocationService,
                                                  AgentWorkflowInstanceService instanceService,
                                                  AgentWorkflowInvocationOutboxService outboxService,
                                                  AgentTaskEventService taskEventService,
                                                  WorkflowOutputResolver outputResolver,
                                                  WorkflowSensitiveDataSanitizer sanitizer,
                                                  AgentWorkflowCapabilityService capabilityService) {
        this.invocationService = invocationService;
        this.instanceService = instanceService;
        this.outboxService = outboxService;
        this.taskEventService = taskEventService;
        this.outputResolver = outputResolver;
        this.sanitizer = sanitizer;
        this.capabilityService = capabilityService;
    }

    @Scheduled(fixedDelayString = "${aether.workflow.agent-invocation-outbox.interval-ms:5000}", initialDelay = 5000L)
    public void process() {
        long now = System.currentTimeMillis();
        syncStateEvents(now);
        for (AgentWorkflowInvocationOutbox event : outboxService.claimPending(now, 100)) deliver(event);
    }

    private void syncStateEvents(long now) {
        List<AgentWorkflowInvocation> invocations = invocationService.list(Wrappers.lambdaQuery(AgentWorkflowInvocation.class)
                .in(AgentWorkflowInvocation::getStatus, "ACCEPTED", "RUNNING", "WAITING_ACTION",
                        "COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT")
                .eq(AgentWorkflowInvocation::getDeleted, false)
                .orderByAsc(AgentWorkflowInvocation::getCreatedAt)
                .last("LIMIT 200"));
        for (AgentWorkflowInvocation invocation : invocations) {
            if (StringUtils.isBlank(invocation.getWorkflowInstanceId())) continue;
            AgentWorkflowInstance instance = instanceService.getById(invocation.getWorkflowInstanceId());
            if (instance == null) continue;
            String projectedStatus = projectInvocationStatus(instance.getStatus());
            if (!StringUtils.equals(projectedStatus, invocation.getStatus())) {
                invocation.setStatus(projectedStatus);
                if (isTerminal(instance.getStatus())) invocation.setCompletedAt(System.currentTimeMillis());
                invocationService.updateById(invocation);
            }
            enqueue(invocation, instance, now);
        }
    }

    private void enqueue(AgentWorkflowInvocation invocation, AgentWorkflowInstance instance, long now) {
        long version = instance.getStateVersion() == null ? 0L : instance.getStateVersion();
        String eventType = eventType(instance.getStatus());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("invocationId", invocation.getId());
        payload.put("instanceId", instance.getId());
        payload.put("workflowId", invocation.getWorkflowId());
        payload.put("workflowVersionId", invocation.getWorkflowVersionId());
        payload.put("status", instance.getStatus());
        payload.put("stateVersion", version);
        payload.put("currentNodeId", instance.getCurrentNodeId());
        if (isTerminal(instance.getStatus())) {
            com.aether.workflow.entity.AgentWorkflowCapability capability = capabilityService.getById(invocation.getCapabilityId());
            payload.put("output", outputResolver.resolve(instance, capability == null ? null : capability.getOutputSchema()));
        }
        AgentWorkflowInvocationOutbox event = new AgentWorkflowInvocationOutbox();
        event.setTenantId(invocation.getTenantId());
        event.setApplicationId(invocation.getApplicationId());
        event.setInvocationId(invocation.getId());
        event.setWorkflowInstanceId(instance.getId());
        event.setEventType(eventType);
        event.setStateVersion(version);
        event.setPayload(sanitizer.sanitizeJson(JSON.toJSONString(payload)));
        event.setStatus("PENDING");
        event.setAttemptCount(0);
        event.setNextAttemptAt(now);
        outboxService.enqueue(event);
    }

    private void deliver(AgentWorkflowInvocationOutbox event) {
        if (event == null) return;
        try {
            AgentWorkflowInvocation invocation = invocationService.getById(event.getInvocationId());
            if (invocation != null && StringUtils.isNotBlank(invocation.getAgentTaskId())) {
                taskEventService.record(invocation.getAgentTaskId(), invocation.getAgentRunId(),
                        event.getEventType(), event.getEventType(), event.getPayload());
            }
            event.setStatus("DELIVERED");
            event.setDeliveredAt(System.currentTimeMillis());
            event.setLastError(null);
            event.setNextAttemptAt(null);
            outboxService.updateById(event);
        } catch (Exception ex) {
            int attempts = event.getAttemptCount() == null ? 1 : event.getAttemptCount();
            event.setLastError(StringUtils.abbreviate(ex.getMessage(), 2048));
            if (attempts >= 10) {
                event.setStatus("FAILED");
                event.setNextAttemptAt(null);
            } else {
                event.setStatus("RETRYING");
                event.setNextAttemptAt(System.currentTimeMillis() + Math.min(3600000L, 1000L << Math.min(10, attempts)));
            }
            outboxService.updateById(event);
        }
    }

    private String projectInvocationStatus(String status) {
        if (isTerminal(status)) return status;
        if (StringUtils.startsWith(status, "WAITING_")) return "WAITING_ACTION";
        return "RUNNING";
    }

    private String eventType(String status) {
        if ("COMPLETED".equals(status)) return "workflow.completed";
        if ("FAILED".equals(status)) return "workflow.failed";
        if ("TERMINATED".equals(status)) return "workflow.terminated";
        if ("TIMED_OUT".equals(status)) return "workflow.timed_out";
        if ("WAITING_USER".equals(status)) return "workflow.waiting_user";
        if ("WAITING_EVENT".equals(status)) return "workflow.waiting_event";
        return "workflow.state_changed";
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status)
                || "TERMINATED".equals(status) || "TIMED_OUT".equals(status);
    }
}
