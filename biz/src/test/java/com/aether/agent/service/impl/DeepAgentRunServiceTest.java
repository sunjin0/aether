package com.aether.agent.service.impl;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.AgentTask;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.*;
import com.aether.agent.vo.AgentRunPlanVo;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证Deep智能体运行服务的行为。
 */
@ExtendWith(MockitoExtension.class)
class DeepAgentRunServiceTest {

    @Mock
    private DeepAgentSigningClient signingClient;
    @Mock
    private AgentRunService agentRunService;
    @Mock
    private AgentRunStepService agentRunStepService;
    @Mock
    private AgentConversationService agentConversationService;
    @Mock
    private AgentMessageService agentMessageService;
    @Mock
    private AgentToolCallLogService toolCallLogService;
    @Mock
    private DelegationTokenService delegationTokenService;
    @Mock
    private AgentToolCatalog toolCatalog;
    @Mock
    private KnowledgeContextService knowledgeContextService;
    @Mock
    private ConversationContextService conversationContextService;
    @Mock
    private AgentSessionService agentSessionService;
    @Mock
    private AgentTaskService agentTaskService;
    @Mock
    private AgentTaskEventService agentTaskEventService;
    @Mock
    private AgentSessionMemoryService agentSessionMemoryService;
    @Mock
    private AdminPreferenceService adminPreferenceService;
    @Mock
    private SkillArtifactExecutionService artifactExecutionService;
    @Mock
    private AgentRunPlanService planService;
    @Mock
    private DeepAgentConfig config;

    private DeepAgentRunService service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentRun.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentConversation.class);
        lenient().when(agentSessionService.claimTask(anyString(), anyString())).thenReturn(true);
        service = new DeepAgentRunService(agentRunService, agentRunStepService,
                signingClient, agentConversationService, agentMessageService,
                toolCallLogService,
                delegationTokenService, toolCatalog, knowledgeContextService, conversationContextService, agentSessionService, agentTaskService, agentTaskEventService, agentSessionMemoryService, adminPreferenceService, artifactExecutionService, planService, org.mockito.Mockito.mock(ToolRouterService.class), config);
    }

    /**
     * 处理start运行CreatesLocal运行AndCallsExternal服务。
     */
    @Test
    void startRunCreatesLocalRunAndCallsExternalService() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setSystemPrompt("你是助手");
        agent.setMaxToolRounds(5);
        agent.setModel("deepseek-v4");

        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            run.setId("run-1");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(eq("run-1"), eq("user-1"), eq("agent-1"), anyList()))
                .thenReturn("delegation-jwt");
        when(toolCatalog.getBoundTools("agent-1")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body("{\"run_id\":\"run-1\",\"status\":\"QUEUED\",\"created\":true}"));

        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        when(agentConversationService.getById("conversation-1")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            AgentMessage msg = inv.getArgument(0);
            msg.setId("message-1");
            return true;
        });
        when(conversationContextService.buildDeepSessionMemory("conversation-1"))
                .thenReturn(Collections.singletonList(new com.aether.agent.model.ModelChatMessage("assistant", "前序结论")));
        AgentSession session = new AgentSession();
        session.setId("session-1");
        when(agentSessionService.getOrCreate("conversation-1", "user-1", "agent-1")).thenReturn(session);
        AgentTask taskRecord = new AgentTask();
        taskRecord.setId("task-1");
        when(agentTaskService.create("session-1", "user-1", "agent-1", "你好")).thenReturn(taskRecord);
        AgentSessionMemory durableMemory = new AgentSessionMemory();
        durableMemory.setContent("上次已确认合同存在付款风险");
        when(agentSessionMemoryService.listInjectableForModel("session-1", 12)).thenReturn(Collections.singletonList(durableMemory));
        AdminPreference preference = new AdminPreference();
        preference.setCategory("format");
        preference.setKeyName("output_format");
        preference.setValue("优先使用表格输出");
        when(adminPreferenceService.list(any())).thenReturn(Collections.singletonList(preference));

        String runId = service.startRun(agent, "user-1", "conversation-1", "你好", Collections.emptyList());

        assertEquals("run-1", runId);
        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals(3, runCaptor.getValue().getStatus());
        assertEquals("task-1", runCaptor.getValue().getTaskId());
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(signingClient).signedPost(eq("/v1/runs"), requestCaptor.capture());
        assertEquals("session-1", requestCaptor.getValue().get("session_id"));
        assertEquals("task-1", requestCaptor.getValue().get("task_id"));
        assertEquals("RUNNING", ((Map<String, Object>) requestCaptor.getValue().get("task_state")).get("status"));
        List<Map<String, String>> memory = (List<Map<String, String>>) requestCaptor.getValue().get("conversation_memory");
        assertTrue(memory.get(0).get("content").contains("【用户已确认偏好】"));
        assertEquals("【已完成任务结论】上次已确认合同存在付款风险", memory.get(1).get("content"));
        assertEquals("前序结论", memory.get(2).get("content"));
        assertEquals("DEEP", runCaptor.getValue().getExecutionMode());
        assertEquals("你好", runCaptor.getValue().getInputContent());
        assertEquals("run-1", runCaptor.getValue().getExternalRunId());
        assertEquals("[]", runCaptor.getValue().getRetrievalSources());
        verify(agentConversationService).update(isNull(), any());
    }

    /**
     * 处理start运行QueuesWhen会话AlreadyOwnsAnother任务。
     */
    @Test
    void startRunQueuesWhenSessionAlreadyOwnsAnotherTask() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-queue");
        agent.setSystemPrompt("你是助手");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-queue");
        AgentSession session = new AgentSession();
        session.setId("session-queue");
        AgentTask queuedTask = new AgentTask();
        queuedTask.setId("task-queue");
        when(agentConversationService.getById("conversation-queue")).thenReturn(conversation);
        when(agentSessionService.getOrCreate("conversation-queue", "user-queue", "agent-queue")).thenReturn(session);
        when(agentSessionService.claimTask("session-queue", "task-queue")).thenReturn(false);
        when(agentTaskService.create("session-queue", "user-queue", "agent-queue", "稍后处理")).thenReturn(queuedTask);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.<AgentMessage>getArgument(0).setId("message-queue");
            return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(invocation -> {
            invocation.<AgentRun>getArgument(0).setId("run-queue");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(toolCatalog.getBoundTools("agent-queue")).thenReturn(Collections.emptyList());

        assertEquals("run-queue", service.startRun(agent, "user-queue", "conversation-queue", "稍后处理", Collections.emptyList()));

        verify(agentTaskService).updateStatus("task-queue", "QUEUED", "run-queue", "等待当前任务完成");
        verifyNoInteractions(signingClient);
    }

    /**
     * 处理prioritizes会话MemoryRelevantTo当前任务。
     */
    @Test
    @SuppressWarnings("unchecked")
    void prioritizesSessionMemoryRelevantToCurrentTask() {
        AgentSessionMemory unrelated = new AgentSessionMemory();
        unrelated.setImportance(80);
        unrelated.setContent("上次已生成市场报表");
        AgentSessionMemory relevant = new AgentSessionMemory();
        relevant.setImportance(80);
        relevant.setContent("合同风险已完成初步核查");
        when(agentSessionMemoryService.listInjectableForModel("session-memory", 12))
                .thenReturn(Arrays.asList(unrelated, relevant));

        List<Map<String, String>> memory = (List<Map<String, String>>) ReflectionTestUtils.invokeMethod(
                service, "buildConversationMemory", "", "session-memory", "", "继续处理合同风险");

        assertEquals("【已完成任务结论】合同风险已完成初步核查", memory.get(0).get("content"));
    }

    /**
     * 处理start运行ReusesPaused任务用于ExplicitContinuation。
     */
    @Test
    void startRunReusesPausedTaskForExplicitContinuation() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-continue");
        agent.setSystemPrompt("你是助手");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-continue");
        AgentSession session = new AgentSession();
        session.setId("session-continue");
        AgentTask pausedTask = new AgentTask();
        pausedTask.setId("task-paused");
        pausedTask.setStatus("PAUSED");
        pausedTask.setTitle("分析合同风险");
        AgentRun previousRun = new AgentRun();
        previousRun.setAttemptNo(2);
        when(agentConversationService.getById("conversation-continue")).thenReturn(conversation);
        when(agentSessionService.getOrCreate("conversation-continue", "user-continue", "agent-continue")).thenReturn(session);
        when(agentTaskService.findActive("session-continue")).thenReturn(pausedTask);
        when(agentRunService.getOne(any(), eq(false))).thenReturn(previousRun);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.<AgentMessage>getArgument(0).setId("message-continue");
            return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(invocation -> {
            invocation.<AgentRun>getArgument(0).setId("run-continue");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(toolCatalog.getBoundTools("agent-continue")).thenReturn(Collections.emptyList());
        when(delegationTokenService.create(eq("run-continue"), eq("user-continue"), eq("agent-continue"), anyList())).thenReturn("token");
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        assertEquals("run-continue", service.startRun(agent, "user-continue", "conversation-continue", "继续分析刚才的高风险条款", Collections.emptyList()));

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals("task-paused", runCaptor.getValue().getTaskId());
        assertEquals(3, runCaptor.getValue().getAttemptNo());
        verify(agentTaskService, never()).create(anyString(), anyString(), anyString(), anyString());
        verify(agentSessionService, never()).claimTask(anyString(), anyString());
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(signingClient).signedPost(eq("/v1/runs"), requestCaptor.capture());
        assertEquals("CONTINUE", ((Map<String, Object>) requestCaptor.getValue().get("task_state")).get("routing"));
        verify(agentTaskEventService).record("task-paused", "run-continue", "task.routed", "识别为对暂停任务的明确补充或继续");
    }

    /**
     * 处理start运行PausesStillRunningPredecessorBeforeContinuation。
     */
    @Test
    void startRunPausesStillRunningPredecessorBeforeContinuation() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-cont");
        agent.setSystemPrompt("你是助手");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-cont");
        AgentSession session = new AgentSession();
        session.setId("session-cont");
        AgentTask runningTask = new AgentTask();
        runningTask.setId("task-cont");
        runningTask.setStatus("RUNNING");
        runningTask.setTitle("分析合同风险");
        runningTask.setCurrentRunId("run-prev");
        AgentRun previousRun = new AgentRun();
        previousRun.setId("run-prev");
        previousRun.setExecutionMode("DEEP");
        previousRun.setStatus(4);
        when(agentConversationService.getById("conversation-cont")).thenReturn(conversation);
        when(agentSessionService.getOrCreate("conversation-cont", "user-1", "agent-cont")).thenReturn(session);
        when(agentTaskService.findActive("session-cont")).thenReturn(runningTask);
        when(agentRunService.getById("run-prev")).thenReturn(previousRun);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-cont");
            return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentRun.class).setId("run-new");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(toolCatalog.getBoundTools("agent-cont")).thenReturn(Collections.emptyList());
        when(delegationTokenService.create(eq("run-new"), eq("user-1"), eq("agent-cont"), anyList())).thenReturn("token");
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));
        when(signingClient.signedPost(eq("/v1/runs/run-prev/pause"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        String runId = service.startRun(agent, "user-1", "conversation-cont", "继续分析刚才的高风险条款", Collections.emptyList());

        assertEquals("run-new", runId);
        // 前序 run 仍活跃：派发延续前先暂停旧线程，避免并发改写同一会话上下文。
        verify(signingClient).signedPost(eq("/v1/runs/run-prev/pause"), anyMap());
        ArgumentCaptor<AgentRun> pauseCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService, atLeastOnce()).updateById(pauseCaptor.capture());
        boolean paused = pauseCaptor.getAllValues().stream()
                .anyMatch(u -> "run-prev".equals(u.getId()) && Integer.valueOf(6).equals(u.getStatus()));
        assertTrue(paused, "前序运行应被标记为暂停");
    }

    /**
     * 处理keepsSimilarityIn知识库SourcesSentToDeep智能体。
     */
    @Test
    void keepsSimilarityInKnowledgeSourcesSentToDeepAgent() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentName", "运维手册");
        source.put("documentId", "document-1");
        source.put("chunkId", "chunk-1");
        source.put("content", "知识库内容");
        source.put("citationIndex", 1);
        source.put("similarity", 0.87D);
        source.put("retrievalScore", 0.91D);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service, "buildKnowledgeSources", Collections.singletonList(source));

        assertEquals(0.87D, result.get(0).get("similarity"));
        assertEquals(0.91D, result.get(0).get("retrievalScore"));
    }

    /**
     * 处理start运行KeepsExisting会话Title。
     */
    @Test
    void startRunKeepsExistingConversationTitle() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-title");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-title");
        conversation.setTitle("已有标题");
        when(agentConversationService.getById("conversation-title")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentMessage.class).setId("message-title");
            return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentRun.class).setId("run-title");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(eq("run-title"), anyString(), anyString(), anyList())).thenReturn("token");
        when(toolCatalog.getBoundTools("agent-title")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        service.startRun(agent, "user-title", "conversation-title", "新的任务标题", Collections.emptyList());

        verify(agentConversationService, never()).update(isNull(), any());
    }

    /**
     * 处理start运行PreservesAttachmentMetadataAndPassesItToDeep任务。
     */
    @Test
    void startRunPreservesAttachmentMetadataAndPassesItToDeepTask() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-attachment");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-attachment");
        when(agentConversationService.getById("conversation-attachment")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentMessage.class).setId("message-attachment");
            return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentRun.class).setId("run-attachment");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(eq("run-attachment"), anyString(), anyString(), anyList())).thenReturn("token");
        when(toolCatalog.getBoundTools("agent-attachment")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        service.startRun(agent, "user-attachment", "conversation-attachment", "请总结附件", "附件正文", "[{\"fileName\":\"a.txt\"}]", Collections.emptyList(), null);

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService).save(messageCaptor.capture());
        assertEquals("请总结附件", messageCaptor.getValue().getContent());
        assertEquals("附件正文", messageCaptor.getValue().getAttachmentContent());
        ArgumentCaptor<Map> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(signingClient).signedPost(eq("/v1/runs"), requestCaptor.capture());
        assertEquals("请总结附件\n\n附件内容：\n附件正文", requestCaptor.getValue().get("task"));
    }

    /**
     * 处理start运行FailsWhenExternalReturnsNon202。
     */
    @Test
    void startRunFailsWhenExternalReturnsNon202() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-2");
        agent.setSystemPrompt("test");

        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            run.setId("run-2");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(anyString(), anyString(), anyString(), anyList()))
                .thenReturn("delegation-jwt");
        when(toolCatalog.getBoundTools("agent-2")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error"));

        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-2");
        when(agentConversationService.getById("conversation-2")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            AgentMessage msg = inv.getArgument(0);
            msg.setId("message-2");
            return true;
        });

        assertThrows(RuntimeException.class, () ->
                service.startRun(agent, "user-2", "conversation-2", "hi", Collections.emptyList()));

        verify(agentRunService).update(isNull(), any());
    }

    /**
     * 处理回调SavesStepOnceAndIgnoresDuplicate。
     */
    @Test
    void handleCallbackSavesStepOnceAndIgnoresDuplicate() {
        when(agentRunService.getById("run-1")).thenReturn(deepRun("run-1", "conversation-1", 3));
        when(agentRunStepService.saveIfAbsent(any(AgentRunStep.class)))
                .thenReturn(true).thenReturn(false);

        boolean first = service.handleCallback("run-1", "evt-1", "tool.started", 1000L, "{\"tool\":\"x\"}");
        boolean second = service.handleCallback("run-1", "evt-1", "tool.started", 1000L, "{\"tool\":\"x\"}");

        assertTrue(first);
        assertFalse(second);
        verify(agentRunStepService, times(2)).saveIfAbsent(any(AgentRunStep.class));
    }

    /**
     * 处理paused回调Projects任务And会话State。
     */
    @Test
    void pausedCallbackProjectsTaskAndSessionState() {
        AgentRun run = deepRun("run-paused", "conversation-1", 4);
        run.setSessionId("session-1");
        run.setTaskId("task-1");
        when(agentRunService.getById("run-paused")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true);

        assertTrue(service.markPausedFromCallback("run-paused", "服务重启"));

        verify(agentTaskService).updateStatus("task-1", "PAUSED", "run-paused", "服务重启");
        verify(agentSessionService).updateTaskState("session-1", "task-1", "PAUSED");
    }

    /**
     * 处理ToolStarted回调CreatesPendingPlatformAudit。
     */
    @Test
    void handleToolStartedCallbackCreatesPendingPlatformAudit() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        run.setAgentDefinitionId("agent-1");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunStepService.saveIfAbsent(any(AgentRunStep.class))).thenReturn(true);
        AgentTool tool = new AgentTool();
        tool.setId("tool-1");
        tool.setMcpToolName("get_current_time");
        when(toolCatalog.getBoundTools(anyString())).thenReturn(Collections.singletonList(tool));

        assertTrue(service.handleCallback("run-1", "evt-tool", "tool.started", 1000L,
                "{\"toolCallId\":\"call-1\",\"toolName\":\"get_current_time\",\"arguments\":\"{ }\"}"));

        ArgumentCaptor<AgentToolCallLog> audit = ArgumentCaptor.forClass(AgentToolCallLog.class);
        verify(toolCallLogService).save(audit.capture());
        assertEquals("run-1", audit.getValue().getRunId());
        assertEquals("call-1", audit.getValue().getToolCallId());
        assertEquals("get_current_time", audit.getValue().getToolName());
        assertEquals("tool-1", audit.getValue().getToolId());
        assertEquals(4, audit.getValue().getStatus());
    }

    /**
     * 处理回调RejectsUnknown运行BeforeSavingStep。
     */
    @Test
    void handleCallbackRejectsUnknownRunBeforeSavingStep() {
        when(agentRunService.getById("unknown-run")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                service.handleCallback("unknown-run", "evt-1", "run.started", 1000L, "{}"));

        verify(agentRunStepService, never()).saveIfAbsent(any(AgentRunStep.class));
    }

    /**
     * 处理start运行Registers回调Before分发。
     */
    @Test
    void startRunRegistersCallbackBeforeDispatch() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-3");
        java.util.function.Consumer<String> registration = mock(java.util.function.Consumer.class);
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentRun.class).setId("run-3");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(agentConversationService.getById("conversation-3")).thenReturn(new AgentConversation());
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentMessage.class).setId("message-3");
            return true;
        });
        when(delegationTokenService.create(eq("run-3"), anyString(), anyString(), anyList())).thenReturn("token");
        when(toolCatalog.getBoundTools("agent-3")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenAnswer(inv -> {
            verify(registration).accept("run-3");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("{}");
        });

        service.startRun(agent, "user-3", "conversation-3", "task", Collections.emptyList(), registration);

        InOrder order = inOrder(registration, signingClient);
        order.verify(registration).accept("run-3");
        order.verify(signingClient).signedPost(eq("/v1/runs"), anyMap());
    }

    /**
     * 处理markSucceededReturnsFalseWhen运行判断是否为AlreadyTerminal。
     */
    @Test
    void markSucceededReturnsFalseWhenRunIsAlreadyTerminal() {
        when(agentRunService.update(isNull(), any())).thenReturn(false);

        boolean transitioned = service.markSucceeded("run-1", "final answer", "deep-model", 12, 8, 20);

        assertFalse(transitioned);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(agentRunService).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("status"));
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("IN"));
    }

    /**
     * 处理complete运行DoesNot保存Assistant消息WhenActive状态ClaimFails。
     */
    @Test
    void completeRunDoesNotSaveAssistantMessageWhenActiveStatusClaimFails() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(false);

        DeepAgentRunService.CompletedRun completed = service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null);

        assertNull(completed);
        verify(agentMessageService, never()).save(any(AgentMessage.class));
        verify(agentConversationService, never()).update(isNull(), any());
    }

    /**
     * 处理complete运行FailsWhenAssistant消息CannotBePersisted。
     */
    @Test
    void completeRunFailsWhenAssistantMessageCannotBePersisted() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true);
        when(agentMessageService.save(any(AgentMessage.class))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null));
        verify(agentConversationService, never()).update(isNull(), any());
    }

    /**
     * 处理complete运行AttachesPersisted消息AfterClaimingSuccess。
     */
    @Test
    void completeRunAttachesPersistedMessageAfterClaimingSuccess() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });

        DeepAgentRunService.CompletedRun completed = service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null);

        assertNotNull(completed);
        assertEquals("conversation-1", completed.getConversationId());
        assertEquals("message-1", completed.getMessageId());
        verify(agentRunService, times(2)).update(isNull(), any());
        verify(agentConversationService).update(isNull(), any());
    }

    /**
     * 处理complete运行PersistsLatencyAndRequestTimeAudit。
     */
    @Test
    void completeRunPersistsLatencyAndRequestTimeAudit() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            invocation.getArgument(0, AgentMessage.class).setCreatedAt(1000L);
            return true;
        });

        DeepAgentRunService.CompletedRun completed = service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null, 1500, 2000L);

        assertNotNull(completed);
        assertEquals(Long.valueOf(1000L), completed.getRequestTime());
        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService).save(messageCaptor.capture());
        assertEquals(Integer.valueOf(1500), messageCaptor.getValue().getLatencyMs());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> runUpdateCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(agentRunService, times(2)).update(isNull(), runUpdateCaptor.capture());
        assertTrue(runUpdateCaptor.getAllValues().get(1).getSqlSet().contains("latency_ms"));
    }

    /**
     * 处理complete运行FallsBackToStartedCompletedStepDuration用于Latency。
     */
    @Test
    void completeRunFallsBackToStartedCompletedStepDurationForLatency() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });
        AgentRunStep started = new AgentRunStep();
        started.setOccurredAt(500L);
        when(agentRunStepService.getOne(any(), eq(false))).thenReturn(started);

        service.completeRun("run-1", "final answer", "deep-model", 12, 8, 20,
                null, null, null, null, null, 2000L);

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService).save(messageCaptor.capture());
        assertEquals(Integer.valueOf(1500), messageCaptor.getValue().getLatencyMs());
        verify(agentRunStepService).getOne(any(), eq(false));
    }

    /**
     * 处理complete运行RecordsCitationsAndRetrievalOutcomeFromPersistedSources。
     */
    @Test
    void completeRunRecordsCitationsAndRetrievalOutcomeFromPersistedSources() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        run.setAgentDefinitionId("agent-1");
        run.setInputContent("original task");
        run.setRetrievalSources("[{\"citationIndex\":1,\"chunkId\":\"chunk-1\",\"knowledgeBaseId\":\"kb-1\",\"documentId\":\"doc-1\"}]");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });
        List<Map<String, Object>> cited = Collections.singletonList(source("chunk-1"));
        when(knowledgeContextService.ensureCitations(any(com.aether.agent.model.ModelStreamResponse.class), anyList()))
                .thenReturn(cited);

        service.completeRun("run-1", "final answer【1】", "deep-model", 12, 8, 20,
                null, null, null, null);

        ArgumentCaptor<List<Map<String, Object>>> citedCaptor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeContextService).recordCitations(eq("agent-1"), eq("conversation-1"), eq("message-1"), citedCaptor.capture());
        assertEquals("chunk-1", citedCaptor.getValue().get(0).get("chunkId"));
        verify(knowledgeContextService).recordRetrievalOutcome(eq("agent-1"), eq("conversation-1"), eq("message-1"),
                eq("original task"), anyList(), eq(citedCaptor.getValue()));
    }

    /**
     * 处理complete运行MalformedSourcesRecordsEmptyOutcomeAndStaleCompletionDoesNotAudit。
     */
    @Test
    void completeRunMalformedSourcesRecordsEmptyOutcomeAndStaleCompletionDoesNotAudit() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        run.setAgentDefinitionId("agent-1");
        run.setInputContent("original task");
        run.setRetrievalSources("not-json");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });

        service.completeRun("run-1", "final answer", "deep-model", 12, 8, 20,
                null, null, null, null);

        verify(knowledgeContextService).recordRetrievalOutcome(eq("agent-1"), eq("conversation-1"), eq("message-1"),
                eq("original task"), eq(Collections.emptyList()), eq(Collections.emptyList()));
        reset(knowledgeContextService);
        when(agentRunService.update(isNull(), any())).thenReturn(false);

        assertNull(service.completeRun("run-1", "late answer", "deep-model", null, null, null,
                null, null, null, null));
        verifyNoInteractions(knowledgeContextService);
    }

    /**
     * 处理resumePlanApprovalForwardsSelectedStepsToDeep智能体。
     */
    @Test
    void resumePlanApprovalForwardsSelectedStepsToDeepAgent() {
        AgentMessage message = new AgentMessage();
        message.setId("msg-plan");
        message.setConversationId("conversation-1");
        message.setInteractionStatus("pending");
        message.setQuestionConfig("{\"approvalType\":\"deep_plan_approval\",\"runId\":\"run-1\"}");
        when(agentMessageService.getById("msg-plan")).thenReturn(message);
        AgentRun run = deepRun("run-1", "conversation-1", 6);
        run.setUserId("user-1");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentMessageService.updateById(any(AgentMessage.class))).thenReturn(true);
        when(signingClient.signedPost(eq("/v1/runs/run-1/resume"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("selected_steps", Arrays.asList(1, 3));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", "msg-plan");
        payload.put("answers", answers);

        String resumedRunId = service.resumeToolApproval("conversation-1", "msg-plan", "user-1", payload);

        assertEquals("run-1", resumedRunId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(signingClient).signedPost(eq("/v1/runs/run-1/resume"), requestCaptor.capture());
        assertEquals(Boolean.TRUE, requestCaptor.getValue().get("plan_approved"));
        assertEquals(answers, requestCaptor.getValue().get("answers"));
    }

    /**
     * 处理resumePlanApprovalForwardsFeedbackWithoutRunningTransition。
     */
    @Test
    void resumePlanApprovalForwardsFeedbackWithoutRunningTransition() {
        AgentMessage message = new AgentMessage();
        message.setId("msg-plan");
        message.setConversationId("conversation-1");
        message.setInteractionStatus("pending");
        message.setQuestionConfig("{\"approvalType\":\"deep_plan_approval\",\"runId\":\"run-1\"}");
        when(agentMessageService.getById("msg-plan")).thenReturn(message);
        AgentRun run = deepRun("run-1", "conversation-1", 6);
        run.setUserId("user-1");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentMessageService.updateById(any(AgentMessage.class))).thenReturn(true);
        when(signingClient.signedPost(eq("/v1/runs/run-1/resume"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("plan_feedback", "简化步骤，合并为3步");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", "msg-plan");
        payload.put("answers", answers);

        String resumedRunId = service.resumeToolApproval("conversation-1", "msg-plan", "user-1", payload);

        assertEquals("run-1", resumedRunId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(signingClient).signedPost(eq("/v1/runs/run-1/resume"), requestCaptor.capture());
        assertEquals(Boolean.FALSE, requestCaptor.getValue().get("plan_approved"));
        assertEquals("简化步骤，合并为3步", requestCaptor.getValue().get("plan_feedback"));
        // 反馈路径不置 RUNNING：任务保持 WAITING_APPROVAL，等待重规划后的新审批卡片。
        verify(agentTaskService, never()).updateStatus(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * 解析任务RouteDetectsGoalChangeFromNaturalPhrasing。
     */
    @Test
    void resolveTaskRouteDetectsGoalChangeFromNaturalPhrasing() {
        AgentTask active = new AgentTask();
        active.setId("task-route");
        active.setStatus("PAUSED");
        when(agentTaskService.findActive("session-route")).thenReturn(active);

        Object route = ReflectionTestUtils.invokeMethod(service, "resolveTaskRoute", "session-route", "换个思路，重新规划一遍");
        assertNotNull(route);
        assertEquals("GOAL_CHANGED", ReflectionTestUtils.invokeMethod(route, "name"));
    }

    /**
     * 解析任务RouteContinues用于OrdinaryContinuationWithWhitespace。
     */
    @Test
    void resolveTaskRouteContinuesForOrdinaryContinuationWithWhitespace() {
        AgentTask active = new AgentTask();
        active.setId("task-route2");
        active.setStatus("PAUSED");
        when(agentTaskService.findActive("session-route2")).thenReturn(active);

        Object route = ReflectionTestUtils.invokeMethod(service, "resolveTaskRoute", "session-route2", "继续\n分析  刚才的内容");
        assertNotNull(route);
        assertEquals("CONTINUE", ReflectionTestUtils.invokeMethod(route, "name"));
    }

    /**
     * 处理continuationHintIncludesLatestPlanSteps。
     */
    @Test
    void continuationHintIncludesLatestPlanSteps() {
        AgentTask task = new AgentTask();
        task.setId("task-plan");
        task.setTitle("分析合同风险");
        AgentRunPlanVo planVo = new AgentRunPlanVo();
        AgentRunPlanVo.Version version = new AgentRunPlanVo.Version();
        AgentRunPlanVo.Step done = new AgentRunPlanVo.Step();
        done.setStatus("COMPLETED");
        done.setTitle("步骤一");
        AgentRunPlanVo.Step pending = new AgentRunPlanVo.Step();
        pending.setStatus("PENDING");
        pending.setTitle("步骤二");
        version.setSteps(Arrays.asList(done, pending));
        planVo.setVersions(Collections.singletonList(version));
        when(planService.detailByTaskId("task-plan")).thenReturn(planVo);

        @SuppressWarnings("unchecked")
        Map<String, String> hint = (Map<String, String>) ReflectionTestUtils.invokeMethod(
                service, "continuationContextHint", task);
        assertNotNull(hint);
        assertTrue(hint.get("content").contains("【任务当前计划】"));
        assertTrue(hint.get("content").contains("[完成] 步骤一"));
        assertTrue(hint.get("content").contains("[待办] 步骤二"));
    }

    /**
     * 处理computeWaitingMsSumsAnsweredInteractions。
     */
    @Test
    void computeWaitingMsSumsAnsweredInteractions() {
        AgentMessage a = new AgentMessage();
        a.setCreatedAt(1000L);
        a.setAnsweredAt(5000L);
        AgentMessage b = new AgentMessage();
        b.setCreatedAt(2000L);
        b.setAnsweredAt(3000L);
        when(agentMessageService.list(any())).thenReturn(Arrays.asList(a, b));

        Long waiting = (Long) ReflectionTestUtils.invokeMethod(service, "computeWaitingMs", "conversation-w");
        assertEquals(5000L, waiting);
    }

    /**
     * 处理complete运行WritesWaitingMsTo运行。
     */
    @Test
    void completeRunWritesWaitingMsToRun() {
        AgentRun run = deepRun("run-w", "conversation-w", 4);
        when(agentRunService.getById("run-w")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-w");
            return true;
        });
        AgentMessage interaction = new AgentMessage();
        interaction.setCreatedAt(1000L);
        interaction.setAnsweredAt(6000L);
        when(agentMessageService.list(any())).thenReturn(Collections.singletonList(interaction));

        service.completeRun("run-w", "final", "deep-model", 1, 1, 2,
                null, null, null, null, 1500, 2000L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> runUpdateCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(agentRunService, times(2)).update(isNull(), runUpdateCaptor.capture());
        assertTrue(runUpdateCaptor.getAllValues().get(1).getSqlSet().contains("waiting_ms"));
    }

    /**
     * 处理deep运行。
     */
    private AgentRun deepRun(String runId, String conversationId, int status) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setConversationId(conversationId);
        run.setExecutionMode("DEEP");
        run.setStatus(status);
        run.setDeleted(false);
        return run;
    }

    /**
     * 处理source。
     */
    private Map<String, Object> source(String chunkId) {
        Map<String, Object> source = new HashMap<>();
        source.put("chunkId", chunkId);
        return source;
    }
}
