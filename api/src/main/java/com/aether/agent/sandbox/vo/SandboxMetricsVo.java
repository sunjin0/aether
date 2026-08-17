package com.aether.agent.sandbox.vo;

import lombok.Data;

import java.util.Map;

/**
 * Control-plane observability summary. Every value is calculated from persisted
 * tasks, events, or Runner usage callbacks; the service never invents resource
 * consumption when a Runner did not report it.
 */
@Data
public class SandboxMetricsVo {
    /**
     * Start of the bounded rolling window used by latency and resource totals.
     */
    private Long windowStartAt;
    private Long pendingApproval, queued, running, succeeded, failed, timedOut, cancelled, expired, sensitiveHits;
    private Long terminalTasks, averageQueueWaitMillis, averageExecutionMillis, totalWallMillis, totalOutputBytes;
    private Long registeredRunners, activeRunners, staleRunners;
    /**
     * Percentage in the range 0..100; null when no task has reached a terminal outcome.
     */
    private Double successRatePercent;
    /**
     * Persisted failure code -> count, including Runner and policy rejection categories.
     */
    private Map<String, Long> failureTypes;
    /**
     * Tasks whose frozen policy does not select an image by immutable digest.
     */
    private Long unpinnedImageTaskCount;
}
