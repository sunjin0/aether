package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.aether.workflow.service.AgentWorkflowExecutionJobService;
import com.aether.local.CurrentUser;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowExternalInvocationService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 以数据库任务表驱动流程推进。外部节点无法保证幂等时，异常任务不自动重放，
 * 由业务方确认影响后通过实例重试接口显式恢复。
 */
@Component
public class WorkflowExecutionJobDispatcher {
    private final long leaseMillis;
    private final AgentWorkflowExecutionJobService jobService;
    private final AgentWorkflowExecutionService executionService;
    private final TaskExecutor taskExecutor;
    private final AgentWorkflowExternalInvocationService externalInvocationService;

    /**
     * 创建 {@code WorkflowExecutionJobDispatcher} 实例。
     */
    public WorkflowExecutionJobDispatcher(AgentWorkflowExecutionJobService jobService,
                                          @Lazy AgentWorkflowExecutionService executionService,
                                          @Qualifier("asyncPoolTaskExecutor") TaskExecutor taskExecutor,
                                          AgentWorkflowExternalInvocationService externalInvocationService,
                                          @Value("${aether.workflow.execution.lease-ms:300000}") long leaseMillis) {
        this.jobService = jobService;
        this.executionService = executionService;
        this.taskExecutor = taskExecutor;
        this.externalInvocationService = externalInvocationService;
        this.leaseMillis = Math.max(1000L, leaseMillis);
    }

    /**
     * 同一实例同时只保留一个待处理任务；已完成任务不妨碍下一次重试创建新任务。
     */
    public void enqueueAfterCommit(final String instanceId) {
        if (StringUtils.isBlank(instanceId)) return;
        long now = System.currentTimeMillis();
        AgentWorkflowExecutionJob existing = jobService.getOne(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .eq(AgentWorkflowExecutionJob::getInstanceId, instanceId)
                .in(AgentWorkflowExecutionJob::getStatus, "PENDING", "PROCESSING")
                .eq(AgentWorkflowExecutionJob::getDeleted, false).last("LIMIT 1"));
        if (existing == null) {
            AgentWorkflowExecutionJob job = new AgentWorkflowExecutionJob();
            job.setTenantId(CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId"));
            job.setInstanceId(instanceId);
            job.setStatus("PENDING");
            job.setAttemptCount(0);
            job.setNextAttemptAt(now);
            jobService.save(job);
        }
        Runnable submit = new Runnable() {
            /**
             * 执行当前任务。
             */
            @Override
            public void run() {
                taskExecutor.execute(new Runnable() {
                    /**
                     * 执行当前任务。
                     */
                    @Override
                    public void run() {
                        processInstance(instanceId);
                    }
                });
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 处理afterCommit。
                 */
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else submit.run();
    }

    /**
     * 处理DueJobs。
     */
    @Scheduled(fixedDelayString = "${aether.workflow.execution.scan-interval-ms:1000}", initialDelay = 5000L)
    public void processDueJobs() {
        long now = System.currentTimeMillis();
        List<AgentWorkflowExecutionJob> jobs = jobService.list(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .and(w -> w.eq(AgentWorkflowExecutionJob::getStatus, "PENDING").le(AgentWorkflowExecutionJob::getNextAttemptAt, now)
                        .or().eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING").le(AgentWorkflowExecutionJob::getLockedAt, now - leaseMillis))
                .eq(AgentWorkflowExecutionJob::getDeleted, false).orderByAsc(AgentWorkflowExecutionJob::getNextAttemptAt).last("LIMIT 20"));
        for (final AgentWorkflowExecutionJob job : jobs)
            taskExecutor.execute(new Runnable() {
                /**
                 * 执行当前任务。
                 */
                @Override
                public void run() {
                    processJob(job.getId());
                }
            });
    }

    /**
     * 处理Instance。
     */
    private void processInstance(String instanceId) {
        AgentWorkflowExecutionJob job = jobService.getOne(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .eq(AgentWorkflowExecutionJob::getInstanceId, instanceId).eq(AgentWorkflowExecutionJob::getStatus, "PENDING")
                .eq(AgentWorkflowExecutionJob::getDeleted, false).orderByAsc(AgentWorkflowExecutionJob::getCreatedAt).last("LIMIT 1"));
        if (job != null) processJob(job.getId());
    }

    /**
     * 处理Job。
     */
    private void processJob(String jobId) {
        long now = System.currentTimeMillis();
        AgentWorkflowExecutionJob candidate = jobService.getById(jobId);
        if (candidate == null || Boolean.TRUE.equals(candidate.getDeleted())) return;
        boolean expiredLease = "PROCESSING".equals(candidate.getStatus())
                && candidate.getLockedAt() != null && candidate.getLockedAt() <= now - leaseMillis;
        boolean claimed = jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                .set(AgentWorkflowExecutionJob::getStatus, "PROCESSING").set(AgentWorkflowExecutionJob::getLockedAt, now)
                .setSql("attempt_count = attempt_count + 1")
                .eq(AgentWorkflowExecutionJob::getId, jobId).eq(AgentWorkflowExecutionJob::getDeleted, false)
                .and(w -> w.eq(AgentWorkflowExecutionJob::getStatus, "PENDING")
                        .or().eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING").le(AgentWorkflowExecutionJob::getLockedAt, now - leaseMillis)));
        if (!claimed) return;
        AgentWorkflowExecutionJob job = jobService.getById(jobId);
        if (expiredLease) {
            String error = "后台执行租约已过期，外部节点执行结果未知；请确认后手动重试";
            externalInvocationService.markActiveAsUnknown(job.getInstanceId(), error);
            failJob(job, error);
            return;
        }
        try {
            executionService.executePending(job.getInstanceId());
            jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                    .set(AgentWorkflowExecutionJob::getStatus, "COMPLETED").set(AgentWorkflowExecutionJob::getCompletedAt, System.currentTimeMillis())
                    .set(AgentWorkflowExecutionJob::getErrorMessage, null).eq(AgentWorkflowExecutionJob::getId, jobId).eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING"));
        } catch (Exception ex) {
            String error = StringUtils.defaultIfBlank(StringUtils.abbreviate(ex.getMessage(), 2048), "后台执行任务异常");
            failJob(job, error);
        }
    }

    /**
     * 将异常任务标记失败；不将其重新排队，避免未知的外部副作用被静默重复执行。
     */
    private void failJob(AgentWorkflowExecutionJob job, String error) {
        if (job == null) return;
        boolean failed = jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                .set(AgentWorkflowExecutionJob::getStatus, "FAILED").set(AgentWorkflowExecutionJob::getErrorMessage, error)
                .set(AgentWorkflowExecutionJob::getCompletedAt, System.currentTimeMillis())
                .eq(AgentWorkflowExecutionJob::getId, job.getId()).eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING"));
        if (failed) executionService.failPendingExecution(job.getInstanceId(), error);
    }
}
