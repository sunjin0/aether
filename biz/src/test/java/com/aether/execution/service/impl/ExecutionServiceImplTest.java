package com.aether.execution.service.impl;

import com.aether.execution.entity.Execution;
import com.aether.local.CurrentUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.HashMap;

class ExecutionServiceImplTest {
    @org.junit.jupiter.api.AfterEach
    void clearCurrentUser() { CurrentUser.remove(); }

    @Test
    void startCapturesTenantFromCurrentUser() {
        HashMap<String, String> user = new HashMap<>();
        user.put("tenantId", "tenant-1");
        CurrentUser.set(user);
        ExecutionServiceImpl service = spy(new ExecutionServiceImpl());
        doReturn(true).when(service).save(any(Execution.class));

        Execution result = service.start("AGENT", "trace-1", null, "user-1", "agent-1");

        assertEquals("tenant-1", result.getTenantId());
    }

    @Test
    void startGeneratesTraceAndRunningState() {
        ExecutionServiceImpl service = spy(new ExecutionServiceImpl());
        doAnswer(invocation -> {
            invocation.<Execution>getArgument(0).setId("execution-1");
            return true;
        }).when(service).save(any(Execution.class));

        Execution result = service.start("AGENT", null, null, "user-1", "agent-1");

        assertEquals("execution-1", result.getId());
        assertEquals("AGENT", result.getExecutionType());
        assertEquals("RUNNING", result.getStatus());
        assertNotNull(result.getTraceId());
        assertFalse(result.getTraceId().isEmpty());
        verify(service).save(result);
    }

    @Test
    void finishIsIdempotentAndCalculatesDuration() {
        ExecutionServiceImpl service = spy(new ExecutionServiceImpl());
        Execution execution = new Execution();
        execution.setId("execution-1");
        execution.setStartedAt(System.currentTimeMillis() - 100L);
        execution.setStatus("RUNNING");
        doReturn(execution).when(service).getById("execution-1");
        doReturn(true).when(service).updateById(any(Execution.class));

        assertTrue(service.finish("execution-1", "SUCCEEDED", null, null));
        assertEquals("SUCCEEDED", execution.getStatus());
        assertNotNull(execution.getEndedAt());
        assertTrue(execution.getDurationMs() >= 0L);
        assertFalse(service.finish("execution-1", "FAILED", "late", "duplicate"));
        verify(service, times(1)).updateById(execution);
    }
}
