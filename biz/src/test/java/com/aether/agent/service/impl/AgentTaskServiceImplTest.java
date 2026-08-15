package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentTask;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentTaskServiceImplTest {
    @Test
    void rejectsTerminalTaskRegression() {
        AgentTask completed = new AgentTask(); completed.setId("task-1"); completed.setStatus("COMPLETED");
        AgentTaskServiceImpl service = spy(new AgentTaskServiceImpl());
        doReturn(completed).when(service).getById("task-1");

        service.updateStatus("task-1", "RUNNING", "run-2", null);

        verify(service, never()).updateById(any(AgentTask.class));
    }

    @Test
    void allowsPausedTaskToResume() {
        AgentTask paused = new AgentTask(); paused.setId("task-1"); paused.setStatus("PAUSED");
        AgentTaskServiceImpl service = spy(new AgentTaskServiceImpl());
        doReturn(paused).when(service).getById("task-1");
        doReturn(true).when(service).updateById(any(AgentTask.class));

        service.updateStatus("task-1", "RUNNING", "run-2", null);

        verify(service).updateById(argThat(update -> "RUNNING".equals(update.getStatus()) && "run-2".equals(update.getCurrentRunId())));
    }
}
