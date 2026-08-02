package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 对等待人工输入的业务流程执行 SLA 超时保护。 */
@Component
public class WorkflowInstanceTimeoutScheduler {
    private final AgentWorkflowInstanceService instanceService;
    private final WorkflowCallbackService callbackService;

    public WorkflowInstanceTimeoutScheduler(AgentWorkflowInstanceService instanceService,
                                            WorkflowCallbackService callbackService) {
        this.instanceService = instanceService;
        this.callbackService = callbackService;
    }

    @Scheduled(fixedDelayString = "${aether.workflow.timeout.scan-interval-ms:30000}", initialDelay = 30000L)
    public void timeoutWaitingInstances() {
        long now = System.currentTimeMillis();
        List<AgentWorkflowInstance> candidates = instanceService.list(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getStatus, "WAITING_USER")
                .isNotNull(AgentWorkflowInstance::getDeadlineAt).le(AgentWorkflowInstance::getDeadlineAt, now)
                .orderByAsc(AgentWorkflowInstance::getDeadlineAt).last("LIMIT 100"));
        for (AgentWorkflowInstance candidate : candidates) {
            boolean timedOut = instanceService.update(new LambdaUpdateWrapper<AgentWorkflowInstance>()
                    .set(AgentWorkflowInstance::getStatus, "TIMED_OUT")
                    .set(AgentWorkflowInstance::getErrorMessage, "等待人工操作超时")
                    .set(AgentWorkflowInstance::getCompletedAt, now)
                    .eq(AgentWorkflowInstance::getId, candidate.getId())
                    .eq(AgentWorkflowInstance::getStatus, "WAITING_USER")
                    .le(AgentWorkflowInstance::getDeadlineAt, now));
            if (timedOut) {
                candidate.setStatus("TIMED_OUT"); candidate.setErrorMessage("等待人工操作超时"); candidate.setCompletedAt(now);
                callbackService.recordTerminal(candidate);
            }
        }
    }
}
