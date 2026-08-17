package com.aether.workbench.vo;

import com.aether.workflow.vo.AgentWorkflowVo;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Aggregated, permission-filtered data for the dashboard workbench.
 */
@Data
public class WorkbenchOverviewVo {
    private long waitingWorkflowInstances;
    private long reviewTasks;
    private long runningWorkflowInstances;
    private long failedCallbacks;
    private long executionDeadLetters;
    private List<WorkbenchItemVo> pending = Collections.emptyList();
    private List<WorkbenchItemVo> running = Collections.emptyList();
    private List<WorkbenchItemVo> attention = Collections.emptyList();
    private List<AgentWorkflowVo> quickStartWorkflows = Collections.emptyList();
}
