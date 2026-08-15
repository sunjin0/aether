package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunPlan;
import com.aether.agent.entity.AgentRunPlanStep;
import com.aether.agent.entity.AgentRunPlanVersion;
import com.aether.agent.mapper.AgentRunPlanMapper;
import com.aether.agent.mapper.AgentRunPlanStepMapper;
import com.aether.agent.mapper.AgentRunPlanVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRunPlanServiceImplTest {
    @Mock private AgentRunPlanMapper plans;
    @Mock private AgentRunPlanVersionMapper versions;
    @Mock private AgentRunPlanStepMapper steps;
    @InjectMocks private AgentRunPlanServiceImpl service;

    @Test
    void preservesCompletedStepsAndSupersedesObsoletePendingSteps() {
        AgentRunPlan plan = new AgentRunPlan();
        plan.setId("plan-1"); plan.setRunId("run-1"); plan.setTaskId("task-1"); plan.setCurrentVersion(1);
        AgentRunPlanVersion oldVersion = new AgentRunPlanVersion(); oldVersion.setId("version-1"); oldVersion.setVersion(1);
        AgentRunPlanStep completed = new AgentRunPlanStep();
        completed.setId("old-completed"); completed.setStepKey("inspect"); completed.setStatus("COMPLETED"); completed.setResultSummary("已完成核查"); completed.setAttemptCount(1);
        AgentRunPlanStep obsolete = new AgentRunPlanStep();
        obsolete.setId("old-pending"); obsolete.setStepKey("obsolete"); obsolete.setStatus("PENDING");
        when(plans.selectOne(any())).thenReturn(plan);
        when(versions.selectList(any())).thenReturn(Collections.singletonList(oldVersion));
        when(steps.selectList(any())).thenReturn(Arrays.asList(completed, obsolete));
        doAnswer(invocation -> { invocation.<AgentRunPlanVersion>getArgument(0).setId("version-2"); return 1; }).when(versions).insert(any());
        AtomicInteger sequence = new AtomicInteger();
        doAnswer(invocation -> { invocation.<AgentRunPlanStep>getArgument(0).setId("new-" + sequence.incrementAndGet()); return 1; }).when(steps).insert(any());

        service.recordPlan("run-1", "task-1", "TOOL_RESULT", "工具观察后调整", "{\"tasks\":[{\"id\":\"inspect\",\"title\":\"核查\",\"status\":\"PENDING\"},{\"id\":\"execute\",\"title\":\"执行\",\"status\":\"PENDING\"}]}");

        ArgumentCaptor<AgentRunPlanStep> inserted = ArgumentCaptor.forClass(AgentRunPlanStep.class);
        verify(steps, times(2)).insert(inserted.capture());
        AgentRunPlanStep copiedCompleted = inserted.getAllValues().get(0);
        assertEquals("COMPLETED", copiedCompleted.getStatus());
        assertEquals("已完成核查", copiedCompleted.getResultSummary());
        assertEquals("task-1:inspect", copiedCompleted.getIdempotencyKey());
        assertEquals("task-1:execute", inserted.getAllValues().get(1).getIdempotencyKey());
        verify(steps).updateById(obsolete);
        assertEquals("SUPERSEDED", obsolete.getStatus());
        verify(plans).updateById(argThat(updated -> "new-2".equals(updated.getCurrentStepId())));
    }

    @Test
    void continuesTheSamePlanAcrossTaskRunAttempts() {
        AgentRunPlan plan = new AgentRunPlan();
        plan.setId("plan-task"); plan.setTaskId("task-1"); plan.setRunId("run-old"); plan.setCurrentVersion(1);
        when(plans.selectOne(any())).thenReturn(plan);
        when(versions.selectList(any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> { invocation.<AgentRunPlanVersion>getArgument(0).setId("version-2"); return 1; }).when(versions).insert(any());
        doAnswer(invocation -> { invocation.<AgentRunPlanStep>getArgument(0).setId("step-2"); return 1; }).when(steps).insert(any());

        service.recordPlan("run-new", "task-1", "RESUME", "继续执行", "{\"tasks\":[{\"id\":\"resume\",\"title\":\"继续\",\"status\":\"RUNNING\"}]}");

        assertEquals("run-new", plan.getRunId());
        assertEquals(2, plan.getCurrentVersion());
        verify(plans, never()).insert(any());
        verify(versions).insert(argThat(item -> item.getVersion() == 2 && "RESUME".equals(item.getReason())));
    }
}
