package com.aether.execution.controller;

import com.aether.execution.entity.Execution;
import com.aether.execution.service.ExecutionService;
import com.aether.execution.vo.ExecutionVo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionControllerTest {
    @Test
    void traceReturnsSafeExecutionProjectionInOrder() {
        ExecutionService service = mock(ExecutionService.class);
        Execution first = new Execution();
        first.setId("e1"); first.setTraceId("trace-1"); first.setExecutionType("WORKFLOW"); first.setStatus("SUCCEEDED");
        Execution second = new Execution();
        second.setId("e2"); second.setTraceId("trace-1"); second.setParentExecutionId("e1");
        second.setExecutionType("TOOL"); second.setStatus("FAILED");
        when(service.listByTraceId("trace-1")).thenReturn(Arrays.asList(first, second));

        List<ExecutionVo> result = new ExecutionController(service).trace("trace-1").getData();

        assertEquals(2, result.size());
        assertEquals("WORKFLOW", result.get(0).getExecutionType());
        assertEquals("e1", result.get(1).getParentExecutionId());
        verify(service).listByTraceId("trace-1");
    }

    @Test
    void summaryDelegatesToExecutionService() {
        ExecutionService service = mock(ExecutionService.class);
        when(service.summarize("trace-1")).thenReturn(new com.aether.execution.vo.ExecutionTraceSummaryVo());

        new ExecutionController(service).summary("trace-1");

        verify(service).summarize("trace-1");
    }
}
