package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowAuditEventService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
     * 对人工、业务事件和子流程等待执行 SLA 超时保护；延时节点由专用调度器按 resumeAt 处理。
 */
@Component
public class WorkflowInstanceTimeoutScheduler {
    private final AgentWorkflowInstanceService instanceService;
    private final WorkflowCallbackService callbackService;
    private final AgentWorkflowAuditEventService auditEventService;
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentWorkflowExecutionService executionService;

    /**
     * 创建 {@code WorkflowInstanceTimeoutScheduler} 实例。
     */
    public WorkflowInstanceTimeoutScheduler(AgentWorkflowInstanceService instanceService,
                                            WorkflowCallbackService callbackService, AgentWorkflowAuditEventService auditEventService,
                                            AgentWorkflowNodeInstanceService nodeService, @Lazy AgentWorkflowExecutionService executionService) {
        this.instanceService = instanceService;
        this.callbackService = callbackService;
        this.auditEventService = auditEventService;
        this.nodeService = nodeService;
        this.executionService = executionService;
    }

    /**
     * 处理timeoutWaitingInstances。
     */
    @Scheduled(fixedDelayString = "${aether.workflow.timeout.scan-interval-ms:30000}", initialDelay = 30000L)
    public void timeoutWaitingInstances() {
        long now = System.currentTimeMillis();
        List<AgentWorkflowNodeInstance> waitingEvents = nodeService.list(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getStatus, "WAITING_EVENT").eq(AgentWorkflowNodeInstance::getDeleted, false).last("LIMIT 100"));
        for (AgentWorkflowNodeInstance node : waitingEvents) {
            try {
                JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? null : JSONObject.parseObject(node.getInteractionConfig());
                if (config != null && config.getLongValue("timeoutAt") > 0 && config.getLongValue("timeoutAt") <= now)
                    executionService.timeoutEvent(node.getInstanceId(), node.getNodeId());
            } catch (RuntimeException ignored) { }
        }
        List<AgentWorkflowInstance> candidates = instanceService.list(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .in(AgentWorkflowInstance::getStatus, "WAITING_USER", "WAITING_EVENT", "WAITING_SUBFLOW")
                .isNotNull(AgentWorkflowInstance::getDeadlineAt).le(AgentWorkflowInstance::getDeadlineAt, now)
                .orderByAsc(AgentWorkflowInstance::getDeadlineAt).last("LIMIT 100"));
        for (AgentWorkflowInstance candidate : candidates) {
            boolean timedOut = instanceService.update(new LambdaUpdateWrapper<AgentWorkflowInstance>()
                    .set(AgentWorkflowInstance::getStatus, "TIMED_OUT")
                    .set(AgentWorkflowInstance::getErrorMessage, "工作流等待超时")
                    .set(AgentWorkflowInstance::getCompletedAt, now)
                    .eq(AgentWorkflowInstance::getId, candidate.getId())
                    .in(AgentWorkflowInstance::getStatus, "WAITING_USER", "WAITING_EVENT", "WAITING_SUBFLOW")
                    .le(AgentWorkflowInstance::getDeadlineAt, now));
            if (timedOut) {
                candidate.setStatus("TIMED_OUT");
                candidate.setErrorMessage("工作流等待超时");
                candidate.setCompletedAt(now);
                auditEventService.record(candidate.getId(), null, "INSTANCE_TIMED_OUT", "SYSTEM", "工作流等待超时", null);
                callbackService.recordTerminal(candidate);
            }
        }
    }
}
