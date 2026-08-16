package com.aether.agent.service.impl;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTask;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentTaskService;
import com.aether.agent.service.DeepAgentRunService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deep Agent 卡死运行的兜底扫描：当运行在 QUEUED/RUNNING 状态停留超过阈值
 * (默认 30 分钟)且任务不处于等待用户输入/审批时，将其标记为失败并推进任务队列。
 *
 * deep-agent 自身有 runTimeoutSeconds 超时并会回传失败回调；本扫描器覆盖
 * deep-agent 崩溃、网络分区或回调丢失导致始终无终态回调的场景，避免任务永久
 * 停留在 RUNNING/PLANNING。
 */
@Component
public class DeepRunTimeoutScanner {
    private static final Logger log = LoggerFactory.getLogger(DeepRunTimeoutScanner.class);
    private static final int STATUS_QUEUED = 3;
    private static final int STATUS_RUNNING = 4;

    private final AgentRunService agentRunService;
    private final AgentTaskService agentTaskService;
    private final DeepAgentRunService deepAgentRunService;
    private final DeepAgentConfig config;

    public DeepRunTimeoutScanner(AgentRunService agentRunService, AgentTaskService agentTaskService,
                                 DeepAgentRunService deepAgentRunService, DeepAgentConfig config) {
        this.agentRunService = agentRunService;
        this.agentTaskService = agentTaskService;
        this.deepAgentRunService = deepAgentRunService;
        this.config = config;
    }

    @Scheduled(fixedDelayString = "${aether.deep-agent.timeout-scan-interval-ms:30000}", initialDelay = 60000L)
    public void scanStaleRuns() {
        long timeoutMs = config.getStaleRunTimeoutSeconds() * 1000L;
        if (timeoutMs <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - timeoutMs;
        List<AgentRun> stale = agentRunService.list(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getExecutionMode, "DEEP")
                .in(AgentRun::getStatus, STATUS_QUEUED, STATUS_RUNNING)
                .eq(AgentRun::getDeleted, false)
                .lt(AgentRun::getCreatedAt, cutoff)
                .orderByAsc(AgentRun::getCreatedAt)
                .last("limit 100"));
        if (stale.isEmpty()) {
            return;
        }
        long minutes = timeoutMs / 60000L;
        for (AgentRun run : stale) {
            if (isWaitingForHuman(run)) {
                continue;
            }
            boolean marked = deepAgentRunService.markFailed(run.getId(),
                    "运行超时(" + minutes + " 分钟)未完成，已由平台回收");
            if (marked) {
                log.warn("Deep 运行超时回收: runId={}, ageMs={}", run.getId(),
                        System.currentTimeMillis() - (run.getCreatedAt() == null ? 0L : run.getCreatedAt()));
            }
        }
    }

    /** 等待用户输入或审批的任务不因超时回收——用户可能长时间未处理审批卡片。 */
    private boolean isWaitingForHuman(AgentRun run) {
        if (agentTaskService == null || run == null || run.getTaskId() == null) {
            return false;
        }
        AgentTask task = agentTaskService.getById(run.getTaskId());
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            return false;
        }
        return "WAITING_USER".equals(task.getStatus()) || "WAITING_APPROVAL".equals(task.getStatus());
    }
}
