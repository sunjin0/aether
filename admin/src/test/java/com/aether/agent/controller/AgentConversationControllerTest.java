package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.dto.SessionMemoryCorrectionDto;
import com.aether.agent.dto.AgentControllerRequests.ToolApprovalPolicy;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunContextMetricService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.ConversationCacheService;
import com.aether.agent.service.ConversationSummaryService;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import com.aether.entity.WebResponse;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 验证智能体会话控制器的行为。
 */
@ExtendWith(MockitoExtension.class)
class AgentConversationControllerTest {

    @Mock
    private AgentConversationService conversationService;
    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private AgentMessageService messageService;
    @Mock
    private AgentRunService runService;
    @Mock
    private AgentRunContextMetricService runContextMetricService;
    @Mock
    private AgentSessionService sessionService;
    @Mock
    private AgentSessionMemoryService sessionMemoryService;
    @Mock
    private AgentToolCallLogService toolCallLogService;
    @Mock
    private AdminPreferenceEventService preferenceEventService;
    @Mock
    private AdminPreferenceService preferenceService;
    @Mock
    private ConversationCacheService cacheService;
    @Mock
    private ConversationSummaryService summaryService;
    @Mock
    private AgentToolWorkflow agentToolWorkflow;
    @Mock
    private I18nService i18nService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private AgentConversationController controller;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        initTableInfo(AgentConversation.class);
        initTableInfo(AgentMessage.class);
        initTableInfo(AgentRun.class);
        initTableInfo(AgentRunContextMetric.class);
        initTableInfo(AgentToolCallLog.class);
        initTableInfo(AdminPreferenceEvent.class);
        new I18nUtils(i18nService);
        lenient().when(i18nService.getMessage(any(String.class))).thenReturn("ok");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        HashMap<String, String> user = new HashMap<String, String>();
        user.put("userId", "user-1");
        CurrentUser.set(user);
        controller = new AgentConversationController(
                conversationService, agentDefinitionService, messageService, runService, runContextMetricService,
                sessionService, sessionMemoryService, toolCallLogService,
                preferenceEventService, preferenceService, cacheService, summaryService, agentToolWorkflow,
                redisTemplate);
    }

    /**
     * 消息列表会为助手消息聚合最终上下文容量指标。
     */
    @Test
    void messagesIncludeFinalContextMetricForAssistantRun() {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setUserId("user-1");
        when(conversationService.getOne(any(Wrapper.class))).thenReturn(conversation);

        AgentMessage assistant = new AgentMessage();
        assistant.setId("message-2");
        assistant.setConversationId("conversation-1");
        assistant.setRole("assistant");
        assistant.setMessageType("chat");
        assistant.setContent("完成");
        Page<AgentMessage> page = new Page<AgentMessage>(1, 20);
        page.setRecords(Collections.singletonList(assistant));
        page.setTotal(1);
        when(messageService.page(any(Page.class), any(Wrapper.class))).thenReturn(page);

        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setMessageId("message-2");
        when(runService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(run));
        when(toolCallLogService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        AgentRunContextMetric metric = new AgentRunContextMetric();
        metric.setModelCallId("call-1");
        metric.setRunId("run-1");
        metric.setMetricPhase("FINAL");
        metric.setContextWindowTokens(10000);
        metric.setOutputReserveTokens(1000);
        metric.setSafetyReserveTokens(500);
        metric.setInputBudgetTokens(8500);
        metric.setEstimatedPromptTokens(1000);
        metric.setPromptTokens(1200);
        metric.setToolDefinitionTokens(100);
        metric.setSystemTokens(100);
        metric.setCurrentMessageTokens(50);
        when(runContextMetricService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(metric));

        WebResponse<List<AgentMessageVo>> response = controller.messages("conversation-1", 1L, 20L);

        AgentMessageVo item = response.getData().get(0);
        assertEquals("run-1", item.getRunId());
        assertEquals("call-1", item.getContextMetric().getModelCallId());
        assertEquals(14.12D, item.getContextMetric().getOccupancyPercent());
        assertEquals(100, item.getContextMetric().getToolDefinitionTokens());
        assertEquals(100, item.getContextMetric().getFramingTokens());
    }

    /**
     * 上下文运营指标委托给指标服务聚合。
     */
    @Test
    void contextOperationsMetricsDelegatesToMetricService() {
        AgentContextOperationsMetricsVo metrics = new AgentContextOperationsMetricsVo();
        metrics.setTotalMetricCount(3L);
        when(runContextMetricService.operationsMetrics(123L)).thenReturn(metrics);

        WebResponse<AgentContextOperationsMetricsVo> response = controller.contextOperationsMetrics(123L);

        assertEquals(Long.valueOf(3L), response.getData().getTotalMetricCount());
        verify(runContextMetricService).operationsMetrics(123L);
    }

    /**
     * 处理tearDown。
     */
    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

    /**
     * 删除Cleans会话MemoryAndAuditData。
     */
    @Test
    void deleteCleansConversationMemoryAndAuditData() {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setUserId("user-1");
        when(conversationService.getOne(any(Wrapper.class))).thenReturn(conversation);

        AgentRun run = new AgentRun();
        run.setId("run-1");
        when(runService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(run));
        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setPreferenceId("preference-1");
        when(preferenceEventService.list(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(event));
        when(conversationService.removeById("conversation-1")).thenReturn(true);

        controller.delete("conversation-1");

        verify(toolCallLogService).remove(any(Wrapper.class));
        verify(runService).remove(any(Wrapper.class));
        verify(messageService).remove(any(Wrapper.class));
        verify(preferenceEventService).remove(any(Wrapper.class));
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Collection> preferenceIds =
                ArgumentCaptor.forClass(Collection.class);
        verify(preferenceService).reconcileAfterEvidenceRemoval(preferenceIds.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                preferenceIds.getValue().contains("preference-1"));
        verify(conversationService).removeById("conversation-1");
        verify(cacheService).evict("conversation-1");
        verify(summaryService).evict("conversation-1");
        verify(preferenceService).clearUserCache("user-1");
    }

    /**
     * 处理failed会话删除DoesNotInvalidateMemory。
     */
    @Test
    void failedConversationDeleteDoesNotInvalidateMemory() {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setUserId("user-1");
        when(conversationService.getOne(any(Wrapper.class))).thenReturn(conversation);
        when(runService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(conversationService.removeById("conversation-1")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> controller.delete("conversation-1"));

        verify(cacheService, never()).evict("conversation-1");
        verify(summaryService, never()).evict("conversation-1");
    }

    /**
     * 处理changingToolApprovalPolicyRevokesTemporaryApprovals。
     */
    @Test
    void changingToolApprovalPolicyRevokesTemporaryApprovals() {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setUserId("user-1");
        conversation.setAgentDefinitionId("agent-1");
        when(conversationService.getOne(any(Wrapper.class))).thenReturn(conversation);
        when(conversationService.updateById(any(AgentConversation.class))).thenReturn(true);

        ToolApprovalPolicy request = new ToolApprovalPolicy();
        request.setToolApprovalPolicy("ask");
        controller.updateToolApprovalPolicy("conversation-1", request);

        ArgumentCaptor<AgentConversation> update = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationService).updateById(update.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ask", update.getValue().getToolApprovalPolicy());
        verify(agentToolWorkflow).revokeTemporaryGrants("user-1", "agent-1", "conversation-1");
    }

    /**
     * 记忆修正后会使派生上下文失效。
     */
    @Test
    void correctingMemoryInvalidatesConversationContext() {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setUserId("user-1");
        conversation.setAgentDefinitionId("agent-1");
        when(conversationService.getOne(any(Wrapper.class))).thenReturn(conversation);
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setConversationId("conversation-1");
        session.setUserId("user-1");
        when(sessionService.getOne(any(Wrapper.class))).thenReturn(session);
        AgentSessionMemory replacement = new AgentSessionMemory();
        replacement.setId("memory-2");
        replacement.setContent("项目使用 Java 8");
        when(sessionMemoryService.correctMemory(any(String.class), any(String.class),
                any(String.class), any(String.class), any(Integer.class))).thenReturn(replacement);

        SessionMemoryCorrectionDto dto = new SessionMemoryCorrectionDto();
        dto.setContent("项目使用 Java 8");
        dto.setReason("用户修正运行环境");
        dto.setMemoryVersion(1);

        controller.correctMemory("conversation-1", "memory-1", "\"1\"", "idem-1", dto);

        verify(sessionMemoryService).correctMemory("session-1", "memory-1",
                "项目使用 Java 8", "用户修正运行环境", 1);
        verify(cacheService).evict("conversation-1");
        verify(summaryService).evict("conversation-1");
    }

    /**
     * 记忆写入必须携带幂等键。
     */
    @Test
    void memoryWriteRequiresIdempotencyKey() {
        SessionMemoryCorrectionDto dto = new SessionMemoryCorrectionDto();
        dto.setContent("项目使用 Java 8");
        dto.setReason("用户修正运行环境");

        assertThrows(com.aether.exception.ServerException.class,
                () -> controller.correctMemory("conversation-1", "memory-1", "\"1\"", "", dto));

        verify(sessionMemoryService, never()).correctMemory(anyString(), anyString(), anyString(), anyString(), any());
    }

    /**
     * 重复幂等请求返回首次成功响应。
     */
    @Test
    void duplicateMemoryWriteReturnsCachedResponse() {
        AgentSessionMemory replacement = new AgentSessionMemory();
        replacement.setId("memory-2");
        WebResponse<AgentSessionMemory> cached = WebResponse.OK(replacement);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(cached);

        SessionMemoryCorrectionDto dto = new SessionMemoryCorrectionDto();
        dto.setContent("项目使用 Java 8");
        dto.setReason("用户修正运行环境");

        WebResponse<AgentSessionMemory> response =
                controller.correctMemory("conversation-1", "memory-1", "\"1\"", "idem-1", dto);

        assertEquals("memory-2", response.getData().getId());
        verify(sessionMemoryService, never()).correctMemory(anyString(), anyString(), anyString(), anyString(), any());
        verify(valueOperations).get(anyString());
    }

    /**
     * 处理initTableInfo。
     */
    private void initTableInfo(Class<?> type) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
    }
}
