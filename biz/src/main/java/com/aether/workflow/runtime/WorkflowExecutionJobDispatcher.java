package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.aether.workflow.service.AgentWorkflowExecutionJobService;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 以数据库任务表驱动流程推进。任务可以在进程重启后重新领取，避免 HTTP 请求持有模型调用。
 */
@Component
public class WorkflowExecutionJobDispatcher {
    private static final long LEASE_MILLIS = 5 * 60 * 1000L;
    private final int maxAttempts;
    private final AgentWorkflowExecutionJobService jobService;
    private final AgentWorkflowExecutionService executionService;
    private final TaskExecutor taskExecutor;

    public WorkflowExecutionJobDispatcher(AgentWorkflowExecutionJobService jobService,
                                          @Lazy AgentWorkflowExecutionService executionService,
                                          @Qualifier("asyncPoolTaskExecutor") TaskExecutor taskExecutor,
                                          @org.springframework.beans.factory.annotation.Value("${aether.workflow.execution.max-attempts:8}") int maxAttempts) {
        this.jobService = jobService;
        this.executionService = executionService;
        this.taskExecutor = taskExecutor;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** 同一实例同时只保留一个待处理任务；已完成任务不妨碍下一次重试创建新任务。 */
    public void enqueueAfterCommit(final String instanceId) {
        if (StringUtils.isBlank(instanceId)) return;
        long now = System.currentTimeMillis();
        AgentWorkflowExecutionJob existing = jobService.getOne(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .eq(AgentWorkflowExecutionJob::getInstanceId, instanceId)
                .in(AgentWorkflowExecutionJob::getStatus, "PENDING", "PROCESSING")
                .eq(AgentWorkflowExecutionJob::getDeleted, false).last("LIMIT 1"));
        if (existing == null) {
            AgentWorkflowExecutionJob job = new AgentWorkflowExecutionJob();
            job.setInstanceId(instanceId); job.setStatus("PENDING"); job.setAttemptCount(0); job.setNextAttemptAt(now);
            jobService.save(job);
        }
        Runnable submit = new Runnable() { @Override public void run() { taskExecutor.execute(new Runnable() {
            @Override public void run() { processInstance(instanceId); }
        }); }};
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else submit.run();
    }

    @Scheduled(fixedDelayString = "${aether.workflow.execution.scan-interval-ms:1000}", initialDelay = 5000L)
    public void processDueJobs() {
        long now = System.currentTimeMillis();
        List<AgentWorkflowExecutionJob> jobs = jobService.list(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .and(w -> w.eq(AgentWorkflowExecutionJob::getStatus, "PENDING").le(AgentWorkflowExecutionJob::getNextAttemptAt, now)
                        .or().eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING").le(AgentWorkflowExecutionJob::getLockedAt, now - LEASE_MILLIS))
                .eq(AgentWorkflowExecutionJob::getDeleted, false).orderByAsc(AgentWorkflowExecutionJob::getNextAttemptAt).last("LIMIT 20"));
        for (final AgentWorkflowExecutionJob job : jobs) taskExecutor.execute(new Runnable() {
            @Override public void run() { processJob(job.getId()); }
        });
    }

    private void processInstance(String instanceId) {
        AgentWorkflowExecutionJob job = jobService.getOne(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .eq(AgentWorkflowExecutionJob::getInstanceId, instanceId).eq(AgentWorkflowExecutionJob::getStatus, "PENDING")
                .eq(AgentWorkflowExecutionJob::getDeleted, false).orderByAsc(AgentWorkflowExecutionJob::getCreatedAt).last("LIMIT 1"));
        if (job != null) processJob(job.getId());
    }

    private void processJob(String jobId) {
        long now = System.currentTimeMillis();
        boolean claimed = jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                .set(AgentWorkflowExecutionJob::getStatus, "PROCESSING").set(AgentWorkflowExecutionJob::getLockedAt, now)
                .setSql("attempt_count = attempt_count + 1")
                .eq(AgentWorkflowExecutionJob::getId, jobId).eq(AgentWorkflowExecutionJob::getDeleted, false)
                .and(w -> w.eq(AgentWorkflowExecutionJob::getStatus, "PENDING")
                        .or().eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING").le(AgentWorkflowExecutionJob::getLockedAt, now - LEASE_MILLIS)));
        if (!claimed) return;
        AgentWorkflowExecutionJob job = jobService.getById(jobId);
        try {
            executionService.executePending(job.getInstanceId());
            jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                    .set(AgentWorkflowExecutionJob::getStatus, "COMPLETED").set(AgentWorkflowExecutionJob::getCompletedAt, System.currentTimeMillis())
                    .set(AgentWorkflowExecutionJob::getErrorMessage, null).eq(AgentWorkflowExecutionJob::getId, jobId).eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING"));
        } catch (Exception ex) {
            String error = StringUtils.defaultIfBlank(StringUtils.abbreviate(ex.getMessage(), 2048), "后台执行任务异常");
            int attempts = job.getAttemptCount() == null ? 1 : job.getAttemptCount();
            if (attempts >= maxAttempts) {
                boolean failed = jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                        .set(AgentWorkflowExecutionJob::getStatus, "FAILED").set(AgentWorkflowExecutionJob::getErrorMessage, error)
                        .set(AgentWorkflowExecutionJob::getCompletedAt, System.currentTimeMillis())
                        .eq(AgentWorkflowExecutionJob::getId, jobId).eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING"));
                if (failed) executionService.failPendingExecution(job.getInstanceId(), error);
            } else {
                jobService.update(new LambdaUpdateWrapper<AgentWorkflowExecutionJob>()
                        .set(AgentWorkflowExecutionJob::getStatus, "PENDING").set(AgentWorkflowExecutionJob::getErrorMessage, error)
                        .set(AgentWorkflowExecutionJob::getNextAttemptAt, System.currentTimeMillis() + retryDelayMillis(attempts))
                        .eq(AgentWorkflowExecutionJob::getId, jobId).eq(AgentWorkflowExecutionJob::getStatus, "PROCESSING"));
            }
        }
    }

    /** 10 秒起步、指数退避，上限 5 分钟，避免故障时密集冲击模型或数据库。 */
    private long retryDelayMillis(int attempts) {
        int exponent = Math.min(Math.max(0, attempts - 1), 5);
        return Math.min(300000L, 10000L * (1L << exponent));
    }
}
