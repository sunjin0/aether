package com.aether.workflow.runtime;

import com.aether.workflow.entity.*;
import com.aether.workflow.service.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

/** 按保留期软删除终态流程及其审计副本；0 天表示不自动清理。 */
@Component
public class WorkflowDataRetentionScheduler {
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentWorkflowCallbackDeliveryService callbackService;
    private final AgentWorkflowExecutionJobService jobService;
    private final int retentionDays;

    public WorkflowDataRetentionScheduler(AgentWorkflowInstanceService instanceService, AgentWorkflowNodeInstanceService nodeService,
                                          AgentWorkflowCallbackDeliveryService callbackService, AgentWorkflowExecutionJobService jobService,
                                          @org.springframework.beans.factory.annotation.Value("${aether.workflow.security.retention-days:90}") int retentionDays) {
        this.instanceService = instanceService; this.nodeService = nodeService; this.callbackService = callbackService; this.jobService = jobService;
        this.retentionDays = Math.max(0, retentionDays);
    }

    @Scheduled(cron = "${aether.workflow.security.retention-cron:0 30 3 * * ?}")
    public void purgeExpiredTerminalInstances() {
        if (retentionDays <= 0) return;
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
        List<AgentWorkflowInstance> expired = instanceService.list(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .in(AgentWorkflowInstance::getStatus, Arrays.asList("COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT"))
                .le(AgentWorkflowInstance::getCompletedAt, cutoff).eq(AgentWorkflowInstance::getDeleted, false)
                .orderByAsc(AgentWorkflowInstance::getCompletedAt).last("LIMIT 100"));
        for (AgentWorkflowInstance instance : expired) {
            instanceService.update(new LambdaUpdateWrapper<AgentWorkflowInstance>().set(AgentWorkflowInstance::getDeleted, true).eq(AgentWorkflowInstance::getId, instance.getId()));
            nodeService.update(new LambdaUpdateWrapper<AgentWorkflowNodeInstance>().set(AgentWorkflowNodeInstance::getDeleted, true).eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()));
            callbackService.update(new LambdaUpdateWrapper<AgentWorkflowCallbackDelivery>().set(AgentWorkflowCallbackDelivery::getDeleted, true).eq(AgentWorkflowCallbackDelivery::getInstanceId, instance.getId()));
            jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>().set(AgentWorkflowExecutionJob::getDeleted, true).eq(AgentWorkflowExecutionJob::getInstanceId, instance.getId()));
        }
    }
}
