package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.service.SandboxTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Makes approval expiry and lost Runner leases observable even when no Runner polls.
 */
@Component
public class SandboxTaskRecoveryScheduler {
    private final SandboxTaskService tasks;

    /**
     * 创建 {@code SandboxTaskRecoveryScheduler} 实例。
     */
    public SandboxTaskRecoveryScheduler(SandboxTaskService tasks) {
        this.tasks = tasks;
    }

    /**
     * 处理recover。
     */
    @Scheduled(fixedDelayString = "${aether.sandbox.recovery-interval-ms:30000}", initialDelay = 30000L)
    public void recover() {
        tasks.recoverExpiredTasks();
    }

    /**
     * 处理purgeRetentionData。
     */
    @Scheduled(cron = "${aether.sandbox.retention-cleanup-cron:0 35 3 * * ?}")
    public void purgeRetentionData() {
        tasks.purgeExpiredRetentionData();
    }
}
