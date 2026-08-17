package com.aether.agent.service.impl;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTask;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentTaskService;
import com.aether.agent.service.DeepAgentRunService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证Deep运行TimeoutScanner的行为。
 */
@ExtendWith(MockitoExtension.class)
class DeepRunTimeoutScannerTest {

    @Mock
    private AgentRunService agentRunService;
    @Mock
    private AgentTaskService agentTaskService;
    @Mock
    private DeepAgentRunService deepAgentRunService;
    @Mock
    private DeepAgentConfig config;

    /**
     * 处理marksStaleRunning运行FailedWhen任务判断是否为NotWaiting用于Human。
     */
    @Test
    void marksStaleRunningRunFailedWhenTaskIsNotWaitingForHuman() {
        when(config.getStaleRunTimeoutSeconds()).thenReturn(1800L);
        AgentRun run = run("run-1", 4);
        when(agentRunService.list(any())).thenReturn(Collections.singletonList(run));
        AgentTask task = new AgentTask();
        task.setId("task-1");
        task.setStatus("RUNNING");
        when(agentTaskService.getById("task-1")).thenReturn(task);
        when(deepAgentRunService.markFailed(eq("run-1"), anyString())).thenReturn(true);

        scanner().scanStaleRuns();

        verify(deepAgentRunService).markFailed(eq("run-1"), anyString());
    }

    /**
     * 处理skipsStale运行When任务判断是否为Waiting用于Approval。
     */
    @Test
    void skipsStaleRunWhenTaskIsWaitingForApproval() {
        when(config.getStaleRunTimeoutSeconds()).thenReturn(1800L);
        AgentRun run = run("run-2", 4);
        when(agentRunService.list(any())).thenReturn(Collections.singletonList(run));
        AgentTask task = new AgentTask();
        task.setId("task-2");
        task.setStatus("WAITING_APPROVAL");
        when(agentTaskService.getById("task-2")).thenReturn(task);

        scanner().scanStaleRuns();

        verify(deepAgentRunService, never()).markFailed(anyString(), anyString());
    }

    /**
     * 处理skipsStale运行When任务判断是否为Waiting用于用户。
     */
    @Test
    void skipsStaleRunWhenTaskIsWaitingForUser() {
        when(config.getStaleRunTimeoutSeconds()).thenReturn(1800L);
        AgentRun run = run("run-3", 4);
        when(agentRunService.list(any())).thenReturn(Collections.singletonList(run));
        AgentTask task = new AgentTask();
        task.setId("task-3");
        task.setStatus("WAITING_USER");
        when(agentTaskService.getById("task-3")).thenReturn(task);

        scanner().scanStaleRuns();

        verify(deepAgentRunService, never()).markFailed(anyString(), anyString());
    }

    /**
     * 处理doesNothingWhenNoStaleRuns。
     */
    @Test
    void doesNothingWhenNoStaleRuns() {
        when(config.getStaleRunTimeoutSeconds()).thenReturn(1800L);
        when(agentRunService.list(any())).thenReturn(Collections.emptyList());

        scanner().scanStaleRuns();

        verify(deepAgentRunService, never()).markFailed(anyString(), anyString());
    }

    /**
     * 处理disabledWhenTimeout配置判断是否为Zero。
     */
    @Test
    void disabledWhenTimeoutConfigIsZero() {
        when(config.getStaleRunTimeoutSeconds()).thenReturn(0L);

        scanner().scanStaleRuns();

        verify(agentRunService, never()).list(any());
    }

    /**
     * 执行当前任务。
     */
    private AgentRun run(String id, int status) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setExecutionMode("DEEP");
        run.setStatus(status);
        run.setCreatedAt(1000L);
        run.setTaskId("task-" + id.substring(id.length() - 1));
        run.setDeleted(false);
        return run;
    }

    /**
     * 处理scanner。
     */
    private DeepRunTimeoutScanner scanner() {
        return new DeepRunTimeoutScanner(agentRunService, agentTaskService, deepAgentRunService, config);
    }
}
