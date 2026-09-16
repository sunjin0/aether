package com.aether.workflow.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentRunService;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.runtime.AgentWorkflowNextActionResolver;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowInvocationRecordService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import com.aether.workflow.vo.AgentWorkflowToolOccupant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkflowTaskQueryServiceImplTest {

    /**
     * 参数化查询要断言的是 WHERE / ORDER / LIMIT 本身，而 store 是 mock、根本不看 wrapper，
     * 只能靠捕获 {@code getSqlSegment()}。生成 SQL 片段要求实体已在 lambda 缓存里注册过。
     */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AgentWorkflowInvocation.class);
    }

    @Test
    void aCompletedInstanceHidesItsStillRunningInvocationRow() {
        // invocation.status 只由 observe 和一个只扫最旧 200 条的后台处理器回写，
        // 漏回写的调用会永远停在 RUNNING。照它展示，agent 就会盯着幻影反复观察。
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "COMPLETED"));

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", 20);

        assertTrue(page.getTasks().isEmpty());
        assertFalse(page.isTruncated());
    }

    @Test
    void aMissingInstanceRowFallsBackToTheProjectedStatusInsteadOfFailing() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances();

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", 20);

        assertEquals(1, page.getTasks().size());
        AgentWorkflowTaskVo task = page.getTasks().get(0);
        assertEquals("RUNNING", task.getRawStatus());
        assertEquals("RUNNING", task.getStatus());
        assertEquals("OBSERVE", task.getNextAction().get("type"));
    }

    @Test
    void aDeletedCapabilityDegradesToWaitingForTheHumanWithoutFailing() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "WAITING_ACTION"));
        fixture.instances(instance("instance-1", "WAITING_USER"));
        fixture.capabilities();

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", 20);

        AgentWorkflowTaskVo task = page.getTasks().get(0);
        assertEquals("WAITING_HUMAN", task.getNextAction().get("type"));
        assertEquals("WAITING_USER_INPUT", task.getStatus());
    }

    @Test
    void anAllTerminalListNeverTouchesVersionOrCapability() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"));

        fixture.service.listInFlight("agent-1", "user-1", 20);

        // 终态行没有下一步动作可算；这条钉住查询计划的「终态不加载版本/能力」。
        verify(fixture.versionService, never()).listByIds(any());
        verify(fixture.capabilityService, never()).listByIds(any());
    }

    @Test
    void anMcpApprovalWaitIsDistinguishedFromAPlainUserWait() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "WAITING_ACTION"));
        fixture.instances(instance("instance-1", "WAITING_USER", "approve_1"));
        fixture.nodes(node("instance-1", "approve_1",
                "{\"approvalType\":\"mcp_tool_approval\",\"toolName\":\"send_email\"}"));
        fixture.capabilities(capability("cap-1", "RESOLVE_MCP_APPROVAL"));
        fixture.versions();
        fixture.workflows();

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", 20);

        // invocation 只给了粗粒度的 WAITING_ACTION，这个区分只能来自 resolver。
        assertEquals("WAITING_MCP_APPROVAL", page.getTasks().get(0).getStatus());
        assertEquals("RESOLVE_MCP_APPROVAL", page.getTasks().get(0).getNextAction().get("type"));
    }

    @Test
    void theWorkflowAndCapabilityNamesAreCarriedForDisplay() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "RUNNING"));
        fixture.capabilities(capability("cap-1", "OBSERVE"));
        fixture.versions();
        AgentWorkflow workflow = new AgentWorkflow();
        workflow.setId("workflow-1");
        workflow.setName("请假审批");
        fixture.workflows(workflow);

        AgentWorkflowTaskVo task = fixture.service.listInFlight("agent-1", "user-1", 20).getTasks().get(0);

        assertEquals("请假审批", task.getWorkflowName());
        assertEquals("ticket_flow", task.getCapabilityCode());
        assertEquals("工单流", task.getCapabilityName());
    }

    @Test
    void inFlightStopsAtTheLimitWithoutClaimingTruncationWhileTheWindowHoldsMore() {
        Fixture fixture = new Fixture();
        AgentWorkflowInvocation[] rows = new AgentWorkflowInvocation[12];
        AgentWorkflowInstance[] instances = new AgentWorkflowInstance[12];
        for (int index = 0; index < rows.length; index++) {
            rows[index] = invocation("inv-" + index, "instance-" + index, "cap-1", "RUNNING");
            instances[index] = instance("instance-" + index, "RUNNING");
        }
        fixture.candidates(rows);
        fixture.instances(instances);
        fixture.capabilities(capability("cap-1", "OBSERVE"));

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", 10);

        assertEquals(10, page.getTasks().size());
        assertFalse(page.isTruncated());
    }

    @Test
    void conversationScopeIgnoresRunsFromOtherConversations() {
        Fixture fixture = new Fixture();
        // 服务只查会话名下的运行；这里给不出运行行，等价于 runId 不属于本会话。
        when(fixture.runService.list(any())).thenReturn(Collections.emptyList());

        AgentWorkflowTaskPage page = fixture.service.listByConversation("conv-1", "run-from-elsewhere", true, 1, 20);

        assertTrue(page.getTasks().isEmpty());
        verify(fixture.invocationStore, never()).list(any());
    }

    @Test
    void conversationScopeKeepsTerminalRowsWhenAsked() {
        Fixture fixture = new Fixture();
        AgentRun run = new AgentRun();
        run.setId("run-1");
        when(fixture.runService.list(any())).thenReturn(Collections.singletonList(run));
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        when(fixture.invocationStore.count(any())).thenReturn(1L);

        AgentWorkflowTaskPage page = fixture.service.listByConversation("conv-1", null, true, 1, 20);

        assertEquals(1, page.getTasks().size());
        assertEquals("COMPLETED", page.getTasks().get(0).getStatus());
        assertEquals(1L, page.getTotal());
    }

    @Test
    void aCompletedInvocationIsListedWithItsResultAndResultCode() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        AgentWorkflowInstance instance = instance("instance-1", "COMPLETED");
        instance.setVariables("{\"report\":\"# 分析结果\\n全部完成\"}");
        fixture.instances(instance);
        fixture.workflows(workflow("workflow-1", "文件分析工作流程"));
        fixture.capabilities(capability("cap-1", "OBSERVE", "{\"properties\":{\"report\":{}}}"));
        fixture.versions(version("version-1", "{\"properties\":{\"report\":{}}}"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setWithResult(true);

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        // 「工作流一完成就从清单里消失、结果再也够不到」正是这次要修的事。
        assertEquals(1, page.getTasks().size());
        AgentWorkflowTaskVo task = page.getTasks().get(0);
        assertEquals("COMPLETED", task.getStatus());
        assertEquals("WORKFLOW_COMPLETED", task.getResultCode());
        assertEquals("# 分析结果\n全部完成", task.getOutput().get("report"));
        assertEquals("文件分析工作流程", task.getWorkflowName());
    }

    @Test
    void aPhantomRowWhoseInstanceAlreadyFinishedIsKeptInsteadOfDropped() {
        Fixture fixture = new Fixture();
        // invocation 行没人回写（后台处理器只扫最旧 200 条），实例真值其实早已完成。
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "COMPLETED"));

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", fixture.defaults());

        // 丢弃这类行，等于把已跑完的工作报成不存在 —— 那正是模型编造「已终止」的来路。
        assertEquals(1, page.getTasks().size());
        assertEquals("COMPLETED", page.getTasks().get(0).getStatus());
        assertEquals("WORKFLOW_COMPLETED", page.getTasks().get(0).getResultCode());
    }

    @Test
    void terminalLimitCapsHowManyFinishedInvocationsAreListed() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"),
                invocation("inv-2", "instance-2", "cap-1", "COMPLETED"),
                invocation("inv-3", "instance-3", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"), instance("instance-2", "COMPLETED"),
                instance("instance-3", "COMPLETED"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setTerminalLimit(2);

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        assertEquals(2, page.getTasks().size());
    }

    @Test
    void includeTerminalFalseHidesFinishedRows() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setIncludeTerminal(false);
        options.setWithResult(true);

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        assertTrue(page.getTasks().isEmpty());
        // 关掉之后终态行一条都不查，连带省下版本与能力。
        verify(fixture.versionService, never()).listByIds(any());
        verify(fixture.capabilityService, never()).listByIds(any());
    }

    @Test
    void withResultTrueLoadsVersionsAndCapabilitiesForFinishedRows() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setWithResult(true);

        fixture.service.listInFlight("agent-1", "user-1", options);

        // 不解析结果时这两条查询是纯浪费；要解析结果就必须发出去。
        verify(fixture.versionService).listByIds(any());
        verify(fixture.capabilityService).listByIds(any());
    }

    @Test
    void aToolBeingRunByAWorkflowIsReportedWithItsOwner() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "RUNNING", "tool_1788078153828"));
        fixture.workflows(workflow("workflow-1", "文件分析工作流程"));
        AgentWorkflowNodeInstance node = node("instance-1", "tool_1788078153828", null);
        node.setNodeType("tool");
        fixture.nodes(node);
        AgentWorkflowVersion version = version("version-1", null);
        version.setNodes("[{\"id\":\"tool_1788078153828\",\"type\":\"tool\","
                + "\"resourceId\":\"2081210365587558401\",\"toolName\":\"process_document\"}]");
        fixture.versions(version);

        AgentWorkflowToolOccupant occupant = fixture.service.findRunningToolOwner(
                "agent-1", "user-1", "2081210365587558401");

        // 按 resourceId 精确匹配：它和 agent_tool 的主键是同一个 id，不必按名字猜。
        assertNotNull(occupant);
        assertEquals("inv-1", occupant.getInvocationId());
        assertEquals("文件分析工作流程", occupant.getWorkflowName());
        assertEquals("tool_1788078153828", occupant.getNodeId());
        assertEquals("process_document", occupant.getToolName());
    }

    @Test
    void aDifferentToolOnTheSameNodeIsNotAConflict() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "RUNNING", "tool_1788078153828"));
        AgentWorkflowNodeInstance node = node("instance-1", "tool_1788078153828", null);
        node.setNodeType("tool");
        fixture.nodes(node);
        AgentWorkflowVersion version = version("version-1", null);
        version.setNodes("[{\"id\":\"tool_1788078153828\",\"type\":\"tool\","
                + "\"resourceId\":\"2081210365587558401\",\"toolName\":\"process_document\"}]");
        fixture.versions(version);

        assertNull(fixture.service.findRunningToolOwner("agent-1", "user-1", "some-other-tool"));
    }

    @Test
    void aFinishedWorkflowDoesNotHoldATool() {
        Fixture fixture = new Fixture();
        // invocation 行停在 RUNNING，但实例真值早已结束 —— 幻影不能当成占用者。
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "COMPLETED", "tool_1788078153828"));

        assertNull(fixture.service.findRunningToolOwner("agent-1", "user-1", "2081210365587558401"));
        // 这是每次直接工具调用都要走的热路径：实例已终态就得停在这里，不再往下查节点和版本。
        verify(fixture.nodeService, never()).list(any());
        verify(fixture.versionService, never()).listByIds(any());
    }

    @Test
    void noRunningInvocationShortCircuitsBeforeTouchingAnythingElse() {
        Fixture fixture = new Fixture();
        fixture.candidates();

        assertNull(fixture.service.findRunningToolOwner("agent-1", "user-1", "2081210365587558401"));
        // 绝大多数 agent 名下没有在跑的工作流，这一次查询为空就该结束。
        verify(fixture.instanceService, never()).listByIds(any());
        verify(fixture.nodeService, never()).list(any());
    }

    @Test
    void displayStatusFollowsTheAgreedTokenTable() {
        assertEquals("RUNNING", AgentWorkflowTaskQueryServiceImpl.displayStatus("ACCEPTED", "OBSERVE"));
        assertEquals("RUNNING", AgentWorkflowTaskQueryServiceImpl.displayStatus("RUNNING", "OBSERVE"));
        assertEquals("WAITING_MCP_APPROVAL",
                AgentWorkflowTaskQueryServiceImpl.displayStatus("WAITING_USER", "RESOLVE_MCP_APPROVAL"));
        assertEquals("WAITING_USER_INPUT",
                AgentWorkflowTaskQueryServiceImpl.displayStatus("WAITING_USER", "PROVIDE_AGENT_INPUT"));
        assertEquals("WAITING_USER_INPUT",
                AgentWorkflowTaskQueryServiceImpl.displayStatus("WAITING_USER", "WAITING_HUMAN"));
        // 等外部系统而不是等人：归入运行中，靠 rawStatus 保留细粒度。
        assertEquals("RUNNING", AgentWorkflowTaskQueryServiceImpl.displayStatus("WAITING_EVENT", "SIGNAL_EVENT"));
        assertEquals("RUNNING", AgentWorkflowTaskQueryServiceImpl.displayStatus("WAITING_SUBFLOW", "OBSERVE"));
        assertEquals("FAILED", AgentWorkflowTaskQueryServiceImpl.displayStatus("FAILED", "RETRY_NODE"));
        assertEquals("COMPLETED", AgentWorkflowTaskQueryServiceImpl.displayStatus("COMPLETED", "NONE"));
        assertEquals("TERMINATED", AgentWorkflowTaskQueryServiceImpl.displayStatus("TERMINATED", "NONE"));
        assertEquals("TIMED_OUT", AgentWorkflowTaskQueryServiceImpl.displayStatus("TIMED_OUT", "NONE"));
    }

    @Test
    void aQueryByInvocationIdStillReportsTheInstanceTruthForAPhantomRow() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setInvocationId("inv-1");

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        // 精确查一条是最可能被用到的查询方式。这里若直接吐原始行，模型会拿到一行
        // status=RUNNING 的幻影，并对一个早已结束的工作流无限轮询。
        assertEquals(1, page.getTasks().size());
        assertEquals("COMPLETED", page.getTasks().get(0).getStatus());
        assertEquals("WORKFLOW_COMPLETED", page.getTasks().get(0).getResultCode());
    }

    @Test
    void stateFinishedMatchesRowsWhoseInvocationStatusStillSaysRunning() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setState("finished");

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        // 本次改动最重要的一条回归线：过滤按实例真值走。若照 invocation.status 过滤，
        // finished 会精确地漏掉「跑完了但没人观察过」的行 —— 正是用户最想要的那些。
        assertEquals(1, page.getTasks().size());
        assertEquals("COMPLETED", page.getTasks().get(0).getStatus());
        String sql = fixture.pagedSql();
        assertTrue(sql.contains("agent_workflow_instance"), sql);
        // 裸 SQL 绕过了 @TableLogic 的自动追加，deleted 条件必须自己写。
        assertTrue(sql.contains("deleted = false"), sql);
    }

    @Test
    void stateRunningExplicitlySparesRowsThatHaveNoInstance() {
        Fixture fixture = new Fixture();
        fixture.candidates();
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setState("running");

        fixture.service.listInFlight("agent-1", "user-1", options);

        // NULL NOT IN (...) 求值为 NULL，会把整行丢掉；没有实例 ID 的行必须显式放行。
        String sql = fixture.pagedSql();
        assertTrue(sql.contains("workflow_instance_id IS NULL"), sql);
        assertTrue(sql.contains("NOT IN"), sql);
    }

    @Test
    void aSparsePageNeverClaimsTruncationFromTheTotalAlone() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        fixture.count(7);
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setState("finished");

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        // truncated 若由 total 推，这里会置 true；模型翻到第二页拿到空集却仍看到 true，就此无限翻页。
        assertEquals(7L, page.getTotal());
        assertEquals(1, page.getTasks().size());
        assertFalse(page.isTruncated());
    }

    @Test
    void aFullPageIsFlaggedSoTheModelKnowsToAskForMore() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-1", "instance-1", "cap-1", "COMPLETED"));
        fixture.instances(instance("instance-1", "COMPLETED"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setState("finished");
        options.setPageSize(1);

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        assertTrue(page.isTruncated());
        assertEquals(0L, page.getTotal());
    }

    @Test
    void theCountQueryCarriesNoOrderingOrPaging() {
        Fixture fixture = new Fixture();
        fixture.candidates();
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setState("finished");
        options.setPageSize(20);
        options.setCurrent(2);

        fixture.service.listInFlight("agent-1", "user-1", options);

        // 共用一个已带 LIMIT/OFFSET 的 wrapper 时，PostgreSQL 的聚合行会被 OFFSET 跳过，
        // MyBatis-Plus 再把 null 吞成 0 —— 于是第二页起 total 静默变成 0。
        String count = fixture.countSql();
        assertFalse(count.toUpperCase().contains("ORDER BY"), count);
        assertFalse(count.toUpperCase().contains("LIMIT"), count);
        assertTrue(fixture.pagedSql().contains("LIMIT 20 OFFSET 20"), fixture.pagedSql());
    }

    @Test
    void pagingOrdersByCreatedAtWithTheIdAsATiebreaker() {
        Fixture fixture = new Fixture();
        fixture.candidates();
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setState("finished");

        fixture.service.listInFlight("agent-1", "user-1", options);

        // created_at 是毫秒，同毫秒的行在 LIMIT/OFFSET 下翻页会重复或漏行。
        assertTrue(fixture.pagedSql().contains("ORDER BY created_at DESC,id DESC"), fixture.pagedSql());
    }

    @Test
    void outOfRangePagingParametersAreClampedInsteadOfThrowing() {
        Fixture zero = new Fixture();
        zero.candidates();
        AgentWorkflowTaskListOptions tiny = zero.defaults();
        tiny.setState("finished");
        tiny.setPageSize(0);
        tiny.setCurrent(Integer.MAX_VALUE);

        zero.service.listInFlight("agent-1", "user-1", tiny);

        // 这条路径没有 controller 兜底：pageSize=-1 会变成非法的 LIMIT，
        // 而 (current-1)*pageSize 在 int 下会溢出成负 OFFSET。
        assertTrue(zero.pagedSql().contains("LIMIT 1 OFFSET 1000000"), zero.pagedSql());

        Fixture huge = new Fixture();
        huge.candidates();
        AgentWorkflowTaskListOptions oversized = huge.defaults();
        oversized.setState("finished");
        oversized.setPageSize(100000);

        huge.service.listInFlight("agent-1", "user-1", oversized);

        assertTrue(huge.pagedSql().contains("LIMIT 50 OFFSET 0"), huge.pagedSql());
    }

    @Test
    void pageNumbersAloneKeepTheCuratedOrderWithRunningRowsFirst() {
        Fixture fixture = new Fixture();
        fixture.candidates(invocation("inv-done", "instance-1", "cap-1", "COMPLETED"),
                invocation("inv-live", "instance-2", "cap-1", "RUNNING"));
        fixture.instances(instance("instance-1", "COMPLETED"), instance("instance-2", "RUNNING"));
        AgentWorkflowTaskListOptions options = fixture.defaults();
        options.setPageSize(1);
        options.setCurrent(1);

        AgentWorkflowTaskPage page = fixture.service.listInFlight("agent-1", "user-1", options);

        // 页码若触发模式切换，模型出于谨慎随手传个 pageSize 就会掉进「按创建时间取最新 N 条」，
        // 对一个积压了已结束运行的 agent，可能再也看不到自己正在等的那个工作流。
        assertFalse(options.isQueryRequested());
        assertEquals(1, page.getTasks().size());
        assertEquals("inv-live", page.getTasks().get(0).getInvocationId());
        assertEquals(2L, page.getTotal());
        assertTrue(page.isTruncated());
    }

    @Test
    void blankFiltersAndAllDoNotSwitchTheDataFetchIntoQueryMode() {
        Fixture blank = new Fixture();
        blank.candidates();
        AgentWorkflowTaskListOptions blankOptions = blank.defaults();
        blankOptions.setInvocationId("");
        blankOptions.setState("   ");

        blank.service.listInFlight("agent-1", "user-1", blankOptions);

        // 模型会发空串；若算一次筛选，这次 count 就白发了。
        assertFalse(blankOptions.isQueryRequested());
        verify(blank.invocationStore, never()).count(any());

        Fixture all = new Fixture();
        all.candidates();
        AgentWorkflowTaskListOptions allOptions = all.defaults();
        allOptions.setState("all");

        all.service.listInFlight("agent-1", "user-1", allOptions);

        assertFalse(allOptions.isQueryRequested());
    }

    private static class Fixture {
        final AgentWorkflowInvocationRecordService invocationStore =
                mock(AgentWorkflowInvocationRecordService.class);
        final AgentWorkflowInstanceService instanceService = mock(AgentWorkflowInstanceService.class);
        final AgentWorkflowNodeInstanceService nodeService = mock(AgentWorkflowNodeInstanceService.class);
        final AgentWorkflowVersionService versionService = mock(AgentWorkflowVersionService.class);
        final AgentWorkflowCapabilityService capabilityService = mock(AgentWorkflowCapabilityService.class);
        final AgentWorkflowService workflowService = mock(AgentWorkflowService.class);
        final AgentRunService runService = mock(AgentRunService.class);
        final AgentWorkflowTaskQueryServiceImpl service;

        Fixture() {
            service = new AgentWorkflowTaskQueryServiceImpl(invocationStore, instanceService, nodeService,
                    versionService, capabilityService, workflowService, runService,
                    new AgentWorkflowNextActionResolver(
                            mock(com.aether.workflow.service.AgentWorkflowExternalInvocationService.class)),
                    // 真的输出解析器：解析走的是批量重载，不会回查版本。
                    new com.aether.workflow.runtime.WorkflowOutputResolver(versionService));
        }

        /** 清单默认口径：在跑的 20 条 + 最近结束的 5 条，并解析终态行的结果。 */
        AgentWorkflowTaskListOptions defaults() {
            return new AgentWorkflowTaskListOptions();
        }

        void candidates(AgentWorkflowInvocation... rows) {
            when(invocationStore.list(any())).thenReturn(Arrays.asList(rows));
        }

        /** Mockito 对 {@code long} 的默认返回值就是 0，这里的意义是「把它写成显式的」。 */
        void count(long total) {
            when(invocationStore.count(any())).thenReturn(total);
        }

        /** 参数化查询下翻页那句 list 实际发出的 SQL 片段。 */
        @SuppressWarnings("unchecked")
        String pagedSql() {
            ArgumentCaptor<LambdaQueryWrapper<AgentWorkflowInvocation>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(invocationStore).list(captor.capture());
            return captor.getValue().getSqlSegment();
        }

        /** count 实际发出的 SQL 片段。 */
        @SuppressWarnings("unchecked")
        String countSql() {
            ArgumentCaptor<LambdaQueryWrapper<AgentWorkflowInvocation>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(invocationStore).count(captor.capture());
            return captor.getValue().getSqlSegment();
        }

        void instances(AgentWorkflowInstance... rows) {
            when(instanceService.listByIds(any())).thenReturn(Arrays.asList(rows));
        }

        void capabilities(AgentWorkflowCapability... rows) {
            when(capabilityService.listByIds(any())).thenReturn(Arrays.asList(rows));
        }

        void versions(AgentWorkflowVersion... rows) {
            when(versionService.listByIds(any())).thenReturn(Arrays.asList(rows));
        }

        void workflows(AgentWorkflow... rows) {
            when(workflowService.listByIds(any())).thenReturn(Arrays.asList(rows));
        }

        void nodes(AgentWorkflowNodeInstance... rows) {
            when(nodeService.list(any())).thenReturn(Arrays.asList(rows));
        }
    }

    private static AgentWorkflowInvocation invocation(String id, String instanceId, String capabilityId, String status) {
        AgentWorkflowInvocation invocation = new AgentWorkflowInvocation();
        invocation.setId(id);
        invocation.setWorkflowInstanceId(instanceId);
        invocation.setCapabilityId(capabilityId);
        invocation.setWorkflowId("workflow-1");
        invocation.setWorkflowVersionId("version-1");
        invocation.setAgentRunId("run-1");
        invocation.setStatus(status);
        invocation.setStartedAt(1_700_000_000_000L);
        return invocation;
    }

    private static AgentWorkflowInstance instance(String id, String status) {
        return instance(id, status, null);
    }

    private static AgentWorkflowInstance instance(String id, String status, String currentNodeId) {
        AgentWorkflowInstance instance = new AgentWorkflowInstance();
        instance.setId(id);
        instance.setInvocationId(id.replace("instance", "inv"));
        instance.setWorkflowId("workflow-1");
        instance.setWorkflowVersionId("version-1");
        instance.setStatus(status);
        instance.setCurrentNodeId(currentNodeId);
        instance.setStateVersion(3L);
        return instance;
    }

    private static AgentWorkflowNodeInstance node(String instanceId, String nodeId, String interactionConfig) {
        AgentWorkflowNodeInstance node = new AgentWorkflowNodeInstance();
        node.setId("node-" + nodeId);
        node.setInstanceId(instanceId);
        node.setNodeId(nodeId);
        node.setNodeType("interaction");
        node.setInteractionConfig(interactionConfig);
        return node;
    }

    private static AgentWorkflowCapability capability(String id, String action) {
        return capability(id, action, null);
    }

    private static AgentWorkflowCapability capability(String id, String action, String outputSchema) {
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId(id);
        capability.setCapabilityCode("ticket_flow");
        capability.setDisplayName("工单流");
        capability.setAllowedActions("[\"" + action + "\"]");
        capability.setOutputSchema(outputSchema);
        return capability;
    }

    private static AgentWorkflow workflow(String id, String name) {
        AgentWorkflow workflow = new AgentWorkflow();
        workflow.setId(id);
        workflow.setName(name);
        return workflow;
    }

    private static AgentWorkflowVersion version(String id, String outputSchema) {
        AgentWorkflowVersion version = new AgentWorkflowVersion();
        version.setId(id);
        version.setOutputSchema(outputSchema);
        return version;
    }
}
