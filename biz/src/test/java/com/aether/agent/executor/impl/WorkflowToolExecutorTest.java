package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.service.AgentRunService;
import com.aether.workflow.service.AgentWorkflowInvocationService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import com.aether.utils.TimeUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
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

    @Test
    void listReturnsRunningInvocationsWithoutRequiringAnInvocationId() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(Collections.singletonList(task("inv-1", "RUNNING", "OBSERVE")));
        page.setTotal(1);
        when(tasks.listInFlight(eq("agent-1"), eq("user-1"), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        // 清单工具的参数表只有可选参数：不传 invocationId 是完全合法的调用。
        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"invocations\""));
        assertTrue(result.getContent().contains("\"invocationId\":\"inv-1\""));
        assertTrue(result.getContent().contains("\"stateVersion\":4"));
        assertTrue(result.getContent().contains("\"nextAction\":{\"type\":\"OBSERVE\"}"));
        // 不传 includeCompleted 时要按默认来：在跑的之外也带上最近已结束的，并解析其结果。
        verify(tasks).listInFlight(eq("agent-1"), eq("user-1"), argThat(options ->
                options.getInFlightLimit() == 20 && options.isIncludeTerminal() && options.isWithResult()));
        verifyNoInteractions(invocations);
    }

    @Test
    void anEmptyListIsASuccessfulAnswerRatherThanAFailure() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(new AgentWorkflowTaskPage());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        // 「当前没有在跑的工作流」是有效答案，模型据此可以停止等待，不能判成失败。
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"invocations\":[]"));
        assertTrue(result.getContent().contains("\"total\":0"));
    }

    @Test
    void listReportsTruncationSoTheModelKnowsTheViewIsPartial() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(Collections.singletonList(task("inv-1", "RUNNING", "OBSERVE")));
        page.setTruncated(true);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        assertTrue(result.getContent().contains("\"truncated\":true"));
    }

    @Test
    void aCompletedInvocationIsListedWithItsResult() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        AgentWorkflowTaskVo finished = task("inv-done", "COMPLETED", "NONE");
        finished.setRawStatus("COMPLETED");
        finished.setCompletedAt(1_700_000_900_000L);
        finished.setResultCode("WORKFLOW_COMPLETED");
        finished.setOutput(Collections.<String, Object>singletonMap("report", "# 分析结果\n全部完成"));
        page.setTasks(Collections.singletonList(finished));
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        // 工作流一完成就从清单里消失，模型会把「做完了」读成「不存在了」—— 结果必须够得到。
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"resultCode\":\"WORKFLOW_COMPLETED\""));
        // 时间以 ISO-8601 下发：模型拿着裸毫秒既算不出「多久之前」，也没法直接回填 createdAfter。
        assertTrue(result.getContent().contains("\"completedAt\":\"2023-11-14T22:28:20Z\""), result.getContent());
        assertTrue(result.getContent().contains("全部完成"));
    }

    @Test
    void anOversizedResultFieldIsTruncatedAndFlagged() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        AgentWorkflowTaskVo finished = task("inv-done", "COMPLETED", "NONE");
        finished.setRawStatus("COMPLETED");
        finished.setResultCode("WORKFLOW_COMPLETED");
        finished.setOutput(Collections.<String, Object>singletonMap("report", chars(5000)));
        page.setTasks(Collections.singletonList(finished));
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"outputTruncated\":true"));
        assertTrue(result.getContent().contains("hint"));
        // 结果进的是模型上下文，载荷必须留在压缩器阈值（6000）以下，否则返回形状会被换掉。
        assertTrue(result.getContent().length() < 6000, "payload=" + result.getContent().length());
    }

    @Test
    void aPayloadOverBudgetDropsInlineOutputsEntirely() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        java.util.List<AgentWorkflowTaskVo> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            AgentWorkflowTaskVo finished = task("inv-" + i, "COMPLETED", "NONE");
            finished.setRawStatus("COMPLETED");
            finished.setResultCode("WORKFLOW_COMPLETED");
            finished.setOutput(Collections.<String, Object>singletonMap("report", chars(1200)));
            rows.add(finished);
        }
        page.setTasks(rows);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        // 字段级截断后仍超预算时就整体放弃内联结果：让压缩器插手会把 invocations
        // 换成 {total, sample, truncated}，返回形状整个变样。
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("\"outputOmitted\":true"));
        assertFalse(result.getContent().contains("\"output\":"));
        assertTrue(result.getContent().length() < 6000, "payload=" + result.getContent().length());
        // 结果虽然不内联了，调用本身和结果码仍要在，模型据此知道该去 observe 哪个调用。
        assertTrue(result.getContent().contains("\"resultCode\":\"WORKFLOW_COMPLETED\""));
    }

    @Test
    void includeCompletedFalseSkipsTerminalRowsAndResultResolution() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(new AgentWorkflowTaskPage());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);
        Map<String, Object> args = new HashMap<>();
        args.put("includeCompleted", false);

        executor.execute(context(tool(null, "LIST"), args));

        verify(tasks).listInFlight(eq("agent-1"), eq("user-1"), argThat(options ->
                options.getInFlightLimit() == 20 && !options.isIncludeTerminal() && !options.isWithResult()));
    }

    @Test
    void listTimestampsAreIsoThatTheSameParserReadsBack() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(Collections.singletonList(task("inv-1", "RUNNING", "OBSERVE")));
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(mock(AgentWorkflowInvocationService.class),
                mock(AgentRunService.class), mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        assertTrue(result.getContent().contains("\"startedAt\":\"2023-11-14T22:13:20Z\""), result.getContent());
        // 下发的 ISO 必须能被 createdAfter/createdBefore 的解析器原样读回去（毫秒不变），
        // 否则模型从清单里看到的时间没法拿来问「这条之后还有别的吗」。
        assertEquals(Long.valueOf(1_700_000_000_000L),
                TimeUtils.parseEpochMillis("2023-11-14T22:13:20Z"));
    }

    @Test
    void listAcceptsIsoAndEpochMillisForTheSameBoundary() {
        AgentWorkflowTaskListOptions iso = listOptionsFor("createdAfter", "2026-09-15T10:00:00Z");
        AgentWorkflowTaskListOptions millis = listOptionsFor("createdAfter", "1789466400000");

        assertNotNull(iso.getCreatedAfter(), "带 Z 的 ISO 应当解析成功");
        assertEquals(Long.valueOf(1_789_466_400_000L), iso.getCreatedAfter());
        assertEquals(iso.getCreatedAfter(), millis.getCreatedAfter());
    }

    @Test
    void listRejectsAnUnparseableTimeBoundaryInsteadOfSilentlyIgnoringIt() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(mock(AgentWorkflowInvocationService.class),
                mock(AgentRunService.class), mock(AgentWorkflowCapabilityService.class), tasks);
        Map<String, Object> args = new HashMap<>();
        // 裸日期没有偏移：按系统时区猜会让结果取决于 Pod 落在哪台机器，只能拒绝。
        args.put("createdAfter", "2026-09-15");

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), args));

        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("WORKFLOW_LIST_INVALID_ARGUMENT"), result.getContent());
        // 422 而非 500：这是模型自己把参数写错了，报 500 会让它以为服务端故障，原样重试同一个坏值。
        assertEquals(422, result.getHttpStatus());
        assertTrue(result.getContent().contains("\"retryable\":false"));
        verifyNoInteractions(tasks);
    }

    @Test
    void listTreatsBlankStringsAsAbsentSoTheyDoNotSwitchIntoQueryMode() {
        AgentWorkflowTaskListOptions options = listOptionsFor(
                "invocationId", "", "state", "   ", "capabilityCode", "");

        // 模型会发空串。空串若算一次筛选，模型就会在毫无察觉的情况下丢掉「在跑优先」的保证。
        assertFalse(options.isQueryRequested(), "空串不该把取数切进查询模式");
    }

    @Test
    void listPassesTheStateFilterThrough() {
        AgentWorkflowTaskListOptions options = listOptionsFor("state", "finished", "includeCompleted", true);

        assertTrue(options.isQueryRequested());
        assertEquals("finished", options.getState());
    }

    @Test
    void listResolvesCapabilityCodeToTheBoundCapabilityId() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(new AgentWorkflowTaskPage());
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("cap-1");
        capability.setCapabilityCode("ticket_flow");
        AgentWorkflowCapabilityService capabilities = mock(AgentWorkflowCapabilityService.class);
        when(capabilities.listEnabledForAgent("agent-1", "app-1"))
                .thenReturn(Collections.singletonList(capability));
        WorkflowToolExecutor executor = new WorkflowToolExecutor(mock(AgentWorkflowInvocationService.class),
                mock(AgentRunService.class), capabilities, tasks);
        Map<String, Object> args = new HashMap<>();
        args.put("capabilityCode", "ticket_flow");

        executor.execute(context(tool(null, "LIST"), args));

        ArgumentCaptor<AgentWorkflowTaskListOptions> captor = optionsCaptor(tasks);
        assertEquals("cap-1", captor.getValue().getCapabilityId());
    }

    @Test
    void listRejectsACapabilityThatIsNotBoundToTheAgent() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowCapabilityService capabilities = mock(AgentWorkflowCapabilityService.class);
        when(capabilities.listEnabledForAgent(anyString(), anyString())).thenReturn(Collections.emptyList());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(mock(AgentWorkflowInvocationService.class),
                mock(AgentRunService.class), capabilities, tasks);
        Map<String, Object> args = new HashMap<>();
        args.put("capabilityCode", "other_flow");

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), args));

        // 静默忽略会返回一份没筛过的清单，模型却以为自己问的是那个能力下的调用。
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("WORKFLOW_CAPABILITY_NOT_BOUND"), result.getContent());
        verifyNoInteractions(tasks);
    }

    @Test
    void listReportsReturnedAndTotalSeparately() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(Collections.singletonList(task("inv-1", "RUNNING", "OBSERVE")));
        // total 是 SQL 层的匹配数，可能远大于本页条数（展示状态取自实例真值）。
        page.setTotal(7);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(mock(AgentWorkflowInvocationService.class),
                mock(AgentRunService.class), mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "LIST"), new HashMap<>()));

        assertTrue(result.getContent().contains("\"total\":7"), result.getContent());
        assertTrue(result.getContent().contains("\"returned\":1"), result.getContent());
    }

    @Test
    void listPassesPageNumbersThroughAndLetsIncludeOutputOverrideTheDefault() {
        AgentWorkflowTaskListOptions options = listOptionsFor(
                "current", 2, "pageSize", 5, "includeOutput", false);

        assertEquals(Integer.valueOf(2), options.getCurrent());
        assertEquals(Integer.valueOf(5), options.getPageSize());
        // 默认 includeCompleted=true 会让结果内联；显式关掉时不该再去解析结果。
        assertTrue(options.isIncludeTerminal());
        assertFalse(options.isWithResult());
    }

    @Test
    void missingInvocationIdResolvesToTheOnlyActiveWorkflowInTheConversation() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentRunService runs = mock(AgentRunService.class);
        AgentRun run = new AgentRun();
        run.setConversationId("conv-1");
        when(runs.getById("run-1")).thenReturn(run);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(Collections.singletonList(task("inv-1", "RUNNING", "OBSERVE")));
        when(tasks.listByConversation(eq("conv-1"), isNull(), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(page);
        when(invocations.currentStateVersion("inv-1", "user-1", "agent-1")).thenReturn(4L);
        when(invocations.stop(eq("inv-1"), eq("用户取消了"), eq(4L), eq("user-1"), eq("agent-1")))
                .thenReturn(new AgentWorkflowInvocationService.AgentWorkflowInvocationResult());

        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, runs,
                mock(AgentWorkflowCapabilityService.class), tasks);
        Map<String, Object> args = new HashMap<>();
        args.put("reason", "用户取消了");
        ToolExecutionResult result = executor.execute(context(tool(null, "STOP"), args));

        assertTrue(result.isSuccess(), result.getContent());
        // 模型既没传 invocationId 也没先 observe 拿状态版本，两样都由服务端补齐。
        verify(invocations).stop("inv-1", "用户取消了", 4L, "user-1", "agent-1");
        // 目标按会话取；既然拿到了会话就不该退回到「本 Agent 为本用户启动的」那层更宽的候选。
        verify(tasks, never()).listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class));
    }

    @Test
    void withoutAConversationTheTargetFallsBackToTheAgentsInFlightList() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        // 评测与脚本调用没有会话上下文：run 查不到，只能退回上一层候选。
        when(tasks.listInFlight(eq("agent-1"), eq("user-1"), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(new AgentWorkflowTaskPage());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "STOP"), new HashMap<>()));

        assertFalse(result.isSuccess());
        assertEquals(409, result.getHttpStatus());
        assertTrue(result.getContent().contains("WORKFLOW_TARGET_NOT_FOUND"), result.getContent());
    }

    @Test
    void multipleActiveInvocationsReturnCandidatesInsteadOfPickingOne() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(java.util.Arrays.asList(
                task("inv-1", "RUNNING", "OBSERVE"),
                task("inv-2", "RUNNING", "OBSERVE")));
        when(tasks.listInFlight(eq("agent-1"), eq("user-1"), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);

        ToolExecutionResult result = executor.execute(context(tool(null, "STOP"), new HashMap<>()));

        assertFalse(result.isSuccess());
        assertEquals(409, result.getHttpStatus());
        assertTrue(result.getContent().contains("WORKFLOW_TARGET_AMBIGUOUS"), result.getContent());
        // 候选复用 workflow_list 的字段形状，模型不必学第二套。
        assertTrue(result.getContent().contains("inv-1"));
        assertTrue(result.getContent().contains("inv-2"));
        assertTrue(result.getContent().contains("\"capabilityCode\":\"ticket_flow\""));
        // 停错工作流是破坏性的，宁可多问一句也不挑一条执行。
        verifyNoInteractions(invocations);
    }

    @Test
    void stateBoundActionNarrowsCandidatesToTheOneWaitingForIt() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(java.util.Arrays.asList(
                task("inv-1", "RUNNING", "OBSERVE"),
                task("inv-2", "WAITING_ACTION", "PROVIDE_AGENT_INPUT")));
        when(tasks.listInFlight(eq("agent-1"), eq("user-1"), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(page);
        when(invocations.currentStateVersion("inv-2", "user-1", "agent-1")).thenReturn(9L);
        when(invocations.provideAgentInput(eq("inv-2"), anyMap(), eq(9L), eq("user-1"), eq("agent-1")))
                .thenReturn(new AgentWorkflowInvocationService.AgentWorkflowInvocationResult());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);
        Map<String, Object> args = new HashMap<>();
        args.put("input", Collections.singletonMap("suggestedAssignee", "u-1"));

        ToolExecutionResult result = executor.execute(context(tool(null, "PROVIDE_AGENT_INPUT"), args));

        assertTrue(result.isSuccess(), result.getContent());
        // 只有一条在等 Agent 输入，模型不必再从候选里挑一次。
        verify(invocations).provideAgentInput(eq("inv-2"),
                argThat(value -> "u-1".equals(value.get("suggestedAssignee"))), eq(9L), eq("user-1"), eq("agent-1"));
    }

    @Test
    void stateBoundActionWhenNothingIsWaitingSaysSoInsteadOfPickingOne() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        page.setTasks(java.util.Arrays.asList(
                task("inv-1", "RUNNING", "OBSERVE"),
                task("inv-2", "RUNNING", "OBSERVE")));
        when(tasks.listInFlight(eq("agent-1"), eq("user-1"), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(page);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class),
                mock(AgentWorkflowCapabilityService.class), tasks);
        Map<String, Object> args = new HashMap<>();
        args.put("input", Collections.singletonMap("suggestedAssignee", "u-1"));

        ToolExecutionResult result = executor.execute(context(tool(null, "PROVIDE_AGENT_INPUT"), args));

        assertFalse(result.isSuccess());
        // 不能退回全集随便挑一条：那样必然被领域层的状态前置条件打回，模型只看到一条
        // 看不出所以然的状态错误。这里直接把「没有人在等」说清楚。
        assertTrue(result.getContent().contains("WORKFLOW_TARGET_NOT_WAITING"), result.getContent());
        assertTrue(result.getContent().contains("\"requiredAction\":\"OBSERVE\""));
        verifyNoInteractions(invocations);
    }

    @Test
    void explicitStateVersionStillWinsOverTheServerSideRead() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        when(invocations.stop(eq("inv-1"), any(), eq(7L), eq("user-1"), eq("agent-1")))
                .thenReturn(new AgentWorkflowInvocationService.AgentWorkflowInvocationResult());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        args.put("expectedStateVersion", 7);

        ToolExecutionResult result = executor.execute(context(tool(null, "STOP"), args));

        assertTrue(result.isSuccess(), result.getContent());
        // 历史脚本与非模型内部调用仍然传版本，这条路径不该被新增的服务端读取改掉。
        verify(invocations, never()).currentStateVersion(anyString(), anyString(), anyString());
    }

    /** 带一组参数跑一次清单，返回交给查询服务的取数选项。 */
    private AgentWorkflowTaskListOptions listOptionsFor(Object... pairs) {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        when(tasks.listInFlight(anyString(), anyString(), any(AgentWorkflowTaskListOptions.class)))
                .thenReturn(new AgentWorkflowTaskPage());
        WorkflowToolExecutor executor = new WorkflowToolExecutor(mock(AgentWorkflowInvocationService.class),
                mock(AgentRunService.class), mock(AgentWorkflowCapabilityService.class), tasks);
        Map<String, Object> args = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) args.put((String) pairs[i], pairs[i + 1]);
        executor.execute(context(tool(null, "LIST"), args));
        return optionsCaptor(tasks).getValue();
    }

    private static ArgumentCaptor<AgentWorkflowTaskListOptions> optionsCaptor(AgentWorkflowTaskQueryService tasks) {
        ArgumentCaptor<AgentWorkflowTaskListOptions> captor =
                ArgumentCaptor.forClass(AgentWorkflowTaskListOptions.class);
        verify(tasks).listInFlight(eq("agent-1"), eq("user-1"), captor.capture());
        return captor;
    }

    private static String chars(int count) {
        char[] buffer = new char[count];
        java.util.Arrays.fill(buffer, 'x');
        return new String(buffer);
    }

    @Test
    void actionSpecificStopToolDoesNotRequireActionArgument() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setResultCode("WORKFLOW_STOPPED");
        when(invocations.stop("inv-1", "用户取消", 5L, "user-1", "agent-1")).thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        args.put("expectedStateVersion", 5);
        args.put("reason", "用户取消");

        ToolExecutionResult result = executor.execute(context(tool("cap-1", "STOP"), args));

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("WORKFLOW_STOPPED"));
        verify(invocations).stop("inv-1", "用户取消", 5L, "user-1", "agent-1");
    }

    @Test
    void actionSpecificRetryNodeToolDoesNotRequireActionArgument() {
        AgentWorkflowInvocationService invocations = mock(AgentWorkflowInvocationService.class);
        AgentWorkflowInvocationService.AgentWorkflowInvocationResult expected = new AgentWorkflowInvocationService.AgentWorkflowInvocationResult();
        expected.setResultCode("WORKFLOW_RETRY_ACCEPTED");
        when(invocations.retryNode("inv-1", "node-9", 6L, "user-1", "agent-1")).thenReturn(expected);
        WorkflowToolExecutor executor = new WorkflowToolExecutor(invocations, mock(AgentRunService.class));
        Map<String, Object> args = new HashMap<>();
        args.put("invocationId", "inv-1");
        args.put("expectedStateVersion", 6);
        args.put("nodeId", "node-9");

        ToolExecutionResult result = executor.execute(context(tool("cap-1", "RETRY_NODE"), args));

        assertTrue(result.isSuccess());
        verify(invocations).retryNode("inv-1", "node-9", 6L, "user-1", "agent-1");
    }

    private static AgentWorkflowTaskVo task(String invocationId, String status, String nextActionType) {
        AgentWorkflowTaskVo task = new AgentWorkflowTaskVo();
        task.setInvocationId(invocationId);
        task.setInstanceId("instance-1");
        task.setWorkflowId("workflow-1");
        task.setWorkflowName("请假审批");
        task.setCapabilityCode("ticket_flow");
        task.setStatus(status);
        task.setRawStatus("RUNNING");
        task.setStateVersion(4L);
        task.setCurrentNodeId("approve_1");
        task.setCurrentNodeType("interaction");
        task.setCurrentNodeName("主管审批");
        task.setNextAction(Collections.<String, Object>singletonMap("type", nextActionType));
        task.setStartedAt(1_700_000_000_000L);
        return task;
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
