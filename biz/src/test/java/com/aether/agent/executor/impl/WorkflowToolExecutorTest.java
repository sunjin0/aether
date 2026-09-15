package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.service.AgentRunService;
import com.aether.workflow.service.AgentWorkflowInvocationService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowToolExecutorTest {
    @Test
    void unifiedStartResolvesCapabilityCodeAndPassesOnlyWorkflowInput() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowCapabilityService capabilities = mock(AgentWorkflowCapabilityService.class);
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("cap-1");
        capability.setCapabilityCode("ticket_flow");
        capability.setAllowedActions("[\"START\"]");
        when(capabilities.listEnabledForAgent("agent-1", "app-1"))
                .thenReturn(java.util.Collections.singletonList(capability));
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setInvocationId("inv-unified");
        when(invocations.start(eq("cap-1"), eq("op-unified"),
                argThat(value -> "T-1".equals(value.get("ticketId")) && !value.containsKey("capabilityCode")),
                eq("agent-1"), eq("run-1"), isNull(), eq("tool-call-1"), eq("user-1"), eq("app-1")))
                .thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class), capabilities);
        AgentTool tool = tool(null, "START");
        tool.setName("workflow_start");
        Map<String, Object> args = new HashMap<>();
        args.put("capabilityCode", "ticket_flow");
        args.put("operationKey", "op-unified");
        args.put("input", java.util.Collections.singletonMap("ticketId", "T-1"));
        ToolExecutionResult result = executor.execute(context(tool, args));
        assertTrue(result.isSuccess());
        verify(invocations).start(eq("cap-1"), eq("op-unified"),
                argThat(value -> "T-1".equals(value.get("ticketId")) && !value.containsKey("capabilityCode")),
                eq("agent-1"), eq("run-1"), isNull(), eq("tool-call-1"), eq("user-1"), eq("app-1"));
    }

    @Test
    void startDelegatesCapabilityAndReturnsStructuredResult() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setInvocationId("inv-1");
        expected.setInstanceId("instance-1");
        expected.setStatus("RUNNING");
        when(invocations.start(eq("cap-1"), eq("op-1"), anyMap(), eq("agent-1"), eq("run-1"), isNull(),
                eq("tool-call-1"), eq("user-1"), eq("app-1"))).thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        AgentTool tool = tool("cap-1", "START");
        Map<String, Object> args = new HashMap<>();
        args.put("operationKey", "op-1");
        args.put("ticketId", "T-1");
        ToolExecutionContext context = context(tool, args);
        ToolExecutionResult result = executor.execute(context);
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("inv-1"));
        assertEquals("WORKFLOW", result.getRequestMethod());
        assertTrue(result.getRequestBody().contains("ticketId"));
        assertTrue(result.getRequestBody().contains("operationKey"));
        verify(invocations).start(eq("cap-1"), eq("op-1"), argThat(value -> "T-1".equals(value.get("ticketId"))),
                eq("agent-1"), eq("run-1"), isNull(), eq("tool-call-1"), eq("user-1"), eq("app-1"));
    }

    @Test
    void instanceObserveDelegatesToObserve() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationObservation expected = new AgentWorkflowInvocationObservation();
        expected.setInvocationId("inv-1");
        expected.setStatus("COMPLETED");
        when(invocations.observe("inv-1", "user-1", "agent-1")).thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        AgentTool tool = tool("cap-1", "INSTANCE");
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        args.put("action", "OBSERVE");
        ToolExecutionResult result = executor.execute(context(tool, args));
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("COMPLETED"));
    }

    @Test
    void actionSpecificObserveToolDoesNotRequireActionArgument() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationObservation expected = new AgentWorkflowInvocationObservation();
        expected.setInvocationId("inv-1");
        expected.setStatus("RUNNING");
        when(invocations.observe("inv-1", "user-1", "agent-1")).thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        ToolExecutionResult result = executor.execute(context(tool("cap-1", "OBSERVE"), args));
        assertTrue(result.isSuccess());
        assertEquals("WORKFLOW", result.getRequestMethod());
        assertTrue(result.getRequestBody().contains("invocationId"));
        verify(invocations).observe("inv-1", "user-1", "agent-1");
    }

    @Test
    void domainFailureReturnsStructuredRecoveryContract() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        when(invocations.observe("inv-1", "user-1", "agent-1"))
                .thenThrow(new com.aether.exception.ServerException(409, "Workflow state changed; observe it again before acting"));
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        ToolExecutionResult result = executor.execute(context(tool("cap-1", "OBSERVE"), args));
        assertFalse(result.isSuccess());
        assertEquals(409, result.getHttpStatus());
        assertTrue(result.getContent().contains("WORKFLOW_STATE_CHANGED"));
        assertTrue(result.getContent().contains("\"requiredAction\":\"OBSERVE\""));
    }

    @Test
    void instanceProvidesAgentInputWithExpectedStateVersion() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setResultCode("WORKFLOW_AGENT_INPUT_ACCEPTED");
        when(invocations.provideAgentInput(eq("inv-1"), anyMap(), eq(7L), eq("user-1"), eq("agent-1")))
                .thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        args.put("action", "PROVIDE_AGENT_INPUT");
        args.put("expectedStateVersion", 7);
        args.put("input", java.util.Collections.singletonMap("suggestedAssignee", "u-1"));
        ToolExecutionResult result = executor.execute(context(tool("cap-1", "INSTANCE"), args));
        assertTrue(result.isSuccess());
        verify(invocations).provideAgentInput(eq("inv-1"), argThat(value -> "u-1".equals(value.get("suggestedAssignee"))),
                eq(7L), eq("user-1"), eq("agent-1"));
    }

    @Test
    void instanceResolvesMcpApprovalWithDecision() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setResultCode("WORKFLOW_MCP_APPROVAL_ACCEPTED");
        when(invocations.resolveMcpApproval(eq("inv-1"), eq("once"), eq(8L), eq("user-1"), eq("agent-1")))
                .thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        args.put("action", "RESOLVE_MCP_APPROVAL");
        args.put("decision", "once");
        args.put("expectedStateVersion", 8);
        ToolExecutionResult result = executor.execute(context(tool("cap-1", "INSTANCE"), args));
        assertTrue(result.isSuccess());
        verify(invocations).resolveMcpApproval(eq("inv-1"), eq("once"), eq(8L), eq("user-1"), eq("agent-1"));
    }

    @Test
    void instanceSignalsBusinessEvent() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setResultCode("WORKFLOW_EVENT_ACCEPTED");
        when(invocations.signalEvent(eq("inv-1"), eq("ticket.completed"), eq("event-1"), eq("T-1"), anyMap(),
                eq(3L), eq("user-1"), eq("agent-1"))).thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1"); args.put("action", "SIGNAL_EVENT");
        args.put("expectedStateVersion", 3); args.put("eventType", "ticket.completed");
        args.put("eventId", "event-1"); args.put("correlationKey", "T-1"); args.put("data", java.util.Collections.singletonMap("ok", true));
        ToolExecutionResult result = executor.execute(context(tool("cap-1", "INSTANCE"), args));
        assertTrue(result.isSuccess());
        verify(invocations).signalEvent(eq("inv-1"), eq("ticket.completed"), eq("event-1"), eq("T-1"),
                argThat(value -> Boolean.TRUE.equals(value.get("ok"))), eq(3L), eq("user-1"), eq("agent-1"));
    }

    private ToolExecutionContext context(AgentTool tool, Map<String, Object> args) {
        ToolExecutionContext context = new ToolExecutionContext();
        context.setTool(tool);
        context.setArguments(args);
        context.setAgentDefinitionId("agent-1");
        context.setRunId("run-1");
        context.setUserId("user-1");
        context.setApplicationId("app-1");
        context.setIdempotencyKey("tool-call-1");
        return context;
    }

    private AgentTool tool(String capabilityId, String action) {
        AgentTool tool = new AgentTool();
        tool.setWorkflowCapabilityId(capabilityId);
        tool.setWorkflowToolAction(action);
        tool.setToolType("workflow");
        tool.setType("workflow");
        return tool;
    }
}
