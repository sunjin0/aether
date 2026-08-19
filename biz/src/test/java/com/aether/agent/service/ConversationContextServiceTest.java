package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.service.ConversationSummaryService.SummarySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证会话Context服务的行为。
 */
@ExtendWith(MockitoExtension.class)
class ConversationContextServiceTest {

    @Mock
    private AgentMessageService messageService;
    @Mock
    private ConversationCacheService cacheService;
    @Mock
    private ConversationSummaryService summaryService;
    @Mock
    private AgentRunService runService;
    @Mock
    private AgentToolCallLogService toolCallLogService;
    @Mock
    private AgentSessionService sessionService;
    @Mock
    private AgentSessionMemoryService sessionMemoryService;

    private ConversationContextService service;
    private AgentDefinition agent;
    private ModelProvider provider;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        service = new ConversationContextService(messageService, cacheService, summaryService);
        agent = new AgentDefinition();
        provider = new ModelProvider();
    }

    /**
     * 处理rejectsInstalledSkillContextThatExceedsInputBudget。
     */
    @Test
    void rejectsInstalledSkillContextThatExceedsInputBudget() {
        provider.setContextWindow(1000);
        agent.setMaxTokens(600);
        List<ModelChatMessage> context = Collections.singletonList(
                new ModelChatMessage("system", String.join("", Collections.nCopies(1000, "a"))));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.requireInputBudget(context, agent, provider));

        assertTrue(exception.getMessage().contains("exceeds model input budget"));
        assertEquals(256, service.getInputTokenBudget(agent, provider));
    }

    /**
     * 处理trimsRuntimeRetrievalBeforeProtectedSkillPrompt。
     */
    @Test
    void trimsRuntimeRetrievalBeforeProtectedSkillPrompt() {
        List<ModelChatMessage> context = new ArrayList<>();
        context.add(new ModelChatMessage("system", "版本化 Skill 指令必须保留"));
        context.add(new ModelChatMessage("system", "【运行时上下文】\n" + String.join("", Collections.nCopies(500, "检索片段"))));
        context.add(new ModelChatMessage("user", "请继续"));

        service.enforceTokenBudget(context, 120);

        assertEquals("版本化 Skill 指令必须保留", context.get(0).getContent());
        assertTrue(service.estimateContextTokens(context) <= 120);
    }

    /**
     * 处理long会话WithoutSummaryAlwaysContainsLatest用户消息。
     */
    @Test
    void longConversationWithoutSummaryAlwaysContainsLatestUserMessage() {
        when(messageService.count(any())).thenReturn(36L);
        when(summaryService.get("conversation-1")).thenReturn(null);
        when(messageService.list(any()))
                .thenReturn(messagesDescending(17, 36))
                .thenReturn(messagesAscending(1, 20));

        List<ModelChatMessage> context =
                service.buildWithSummary(agent, provider, "conversation-1");

        assertEquals(20, context.size());
        assertEquals("message-17", context.get(0).getContent());
        assertEquals("message-36", context.get(context.size() - 1).getContent());
        assertEquals("user", context.get(context.size() - 1).getRole());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentMessage>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(summaryService).refreshAsync(eq("conversation-1"), isNull(),
                batchCaptor.capture(), eq(agent), eq(provider));
        assertEquals("message-1", batchCaptor.getValue().get(0).getContent());
        assertEquals("message-20", batchCaptor.getValue().get(19).getContent());
    }

    /**
     * 处理summaryAndMessagesAfterCursorFormContinuousContext。
     */
    @Test
    void summaryAndMessagesAfterCursorFormContinuousContext() {
        SummarySnapshot snapshot = new SummarySnapshot();
        snapshot.setSummary("messages 1 through 20");
        snapshot.setCoveredUntilMessageId("00020");
        snapshot.setCoveredUntilCreatedAt(20L);

        when(messageService.count(any())).thenReturn(36L);
        when(summaryService.get("conversation-1")).thenReturn(snapshot);
        when(messageService.list(any())).thenReturn(messagesAscending(21, 36));

        List<ModelChatMessage> context =
                service.buildWithSummary(agent, provider, "conversation-1");

        assertEquals(17, context.size());
        assertEquals("system", context.get(0).getRole());
        assertTrue(context.get(0).getContent().contains("messages 1 through 20"));
        for (int i = 21; i <= 36; i++) {
            assertEquals("message-" + i, context.get(i - 20).getContent());
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentMessage>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(summaryService).refreshAsync(eq("conversation-1"), eq(snapshot),
                batchCaptor.capture(), eq(agent), eq(provider));
        assertEquals(11, batchCaptor.getValue().size());
        assertEquals("message-21", batchCaptor.getValue().get(0).getContent());
        assertEquals("message-31", batchCaptor.getValue().get(10).getContent());
    }

    /**
     * 处理short会话判断是否为RestoredInChronologicalOrder。
     */
    @Test
    void shortConversationIsRestoredInChronologicalOrder() {
        when(messageService.count(any())).thenReturn(4L);
        when(messageService.list(any())).thenReturn(messagesDescending(1, 4));

        List<ModelChatMessage> context =
                service.buildWithSummary(agent, provider, "conversation-1");

        assertEquals(4, context.size());
        assertEquals("message-1", context.get(0).getContent());
        assertEquals("message-4", context.get(3).getContent());
    }

    /**
     * 用户消息UsesPersistedRewriteWhenBuilding模型Context。
     */
    @Test
    void userMessageUsesPersistedRewriteWhenBuildingModelContext() {
        AgentMessage user = message(1);
        user.setRole("user");
        user.setContent("企业版呢？");
        user.setRewrittenContent("企业版产品的退款期限是多少？");
        when(messageService.list(any())).thenReturn(Collections.singletonList(user));

        List<ModelChatMessage> context = service.buildFromHistory(agent, "conversation-1");

        assertEquals("企业版产品的退款期限是多少？", context.get(0).getContent());
    }

    /**
     * 处理rewrite历史记录PrefersPersistedRewriteAndFallsBackToOriginal。
     */
    @Test
    void rewriteHistoryPrefersPersistedRewriteAndFallsBackToOriginal() {
        AgentMessage rewritten = message(1);
        rewritten.setRole("user");
        rewritten.setContent("企业版呢？");
        rewritten.setRewrittenContent("企业版产品的退款期限是多少？");
        AgentMessage legacy = message(2);
        legacy.setRole("user");
        legacy.setContent("旧问题");
        when(messageService.list(any())).thenReturn(Arrays.asList(legacy, rewritten));

        List<ModelChatMessage> history = service.buildRewriteHistory("conversation-1");

        assertEquals("企业版产品的退款期限是多少？", history.get(0).getContent());
        assertEquals("旧问题", history.get(1).getContent());
    }

    /**
     * 处理persisted消息InvalidatesRatherThanMutatesContext缓存。
     */
    @Test
    void persistedMessageInvalidatesRatherThanMutatesContextCache() {
        service.append("conversation-1", new ModelChatMessage("user", "latest"));

        verify(cacheService).evict("conversation-1");
    }

    /**
     * 处理restoresAuditedToolCallsAs工具结果BeforeTheirAssistantAnswer。
     */
    @Test
    void restoresAuditedToolCallsAsToolResultsBeforeTheirAssistantAnswer() {
        AgentMessage answer = message(1);
        answer.setRole("assistant");
        when(messageService.list(any())).thenReturn(Collections.singletonList(answer));

        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setMessageId(answer.getId());
        run.setDeleted(false);
        when(runService.list(any())).thenReturn(Collections.singletonList(run));

        AgentToolCallLog log = new AgentToolCallLog();
        log.setId("log-1");
        log.setRunId("run-1");
        log.setToolCallId("call-1");
        log.setToolName("lookup");
        log.setArguments("{\"id\":1}");
        log.setResponseBody("{\"name\":\"Aether\"}");
        log.setStatus(0);
        log.setDeleted(false);
        when(toolCallLogService.list(any())).thenReturn(Collections.singletonList(log));

        ConversationContextService toolAwareService = new ConversationContextService(
                messageService, cacheService, summaryService, runService, toolCallLogService);
        List<ModelChatMessage> context =
                toolAwareService.buildFromHistory(agent, "conversation-1");

        assertEquals(2, context.size());
        assertEquals("tool", context.get(0).getRole());
        assertEquals("call-1", context.get(0).getToolCallId());
        assertNull(context.get(0).getToolCalls());
        assertTrue(context.get(0).getContent().contains("lookup"));
        assertTrue(context.get(0).getContent().contains("{\"name\":\"Aether\"}"));
        assertEquals("message-1", context.get(1).getContent());
    }

    /**
     * 处理budgetDropsOld历史记录AndKeepsLatest用户AndLatestToolProtocol。
     */
    @Test
    void budgetDropsOldHistoryAndKeepsLatestUserAndLatestToolProtocol() {
        List<ModelChatMessage> context = new ArrayList<ModelChatMessage>();
        context.add(new ModelChatMessage("system", repeat('s', 200)));
        context.add(new ModelChatMessage("user", repeat('o', 900)));
        context.add(new ModelChatMessage("assistant", repeat('a', 900)));
        context.add(new ModelChatMessage("user", "latest-user"));
        context.add(new ModelChatMessage("assistant", null,
                "[{\"id\":\"call-latest\",\"type\":\"function\"}]", null));
        context.add(new ModelChatMessage("tool", repeat('t', 300), null, "call-latest"));

        service.enforceBudget(context, 800);

        assertTrue(context.stream().noneMatch(item -> repeat('o', 900).equals(item.getContent())));
        assertTrue(context.stream().anyMatch(item -> "latest-user".equals(item.getContent())));
        assertTrue(context.stream().anyMatch(item -> item.getToolCalls() != null
                && item.getToolCalls().contains("call-latest")));
        assertTrue(context.stream().anyMatch(item -> "call-latest".equals(item.getToolCallId())));
    }

    /**
     * 模型WindowAndCompletionReserveDetermine令牌Budget。
     */
    @Test
    void modelWindowAndCompletionReserveDetermineTokenBudget() {
        AgentDefinition configuredAgent = new AgentDefinition();
        configuredAgent.setMaxTokens(200);
        ModelProvider configuredProvider = new ModelProvider();
        configuredProvider.setContextWindow(1000);

        List<ModelChatMessage> context = new ArrayList<ModelChatMessage>();
        context.add(new ModelChatMessage("system", repeat('系', 250)));
        context.add(new ModelChatMessage("user", repeat('旧', 700)));
        context.add(new ModelChatMessage("assistant", repeat('答', 300)));
        context.add(new ModelChatMessage("user", "最新问题"));

        service.enforceBudget(context, configuredAgent, configuredProvider);

        // 1000 - 200 completion reserve - 512 safety reserve = 288 input tokens.
        assertTrue(service.estimateContextTokens(context) <= 288);
        assertTrue(context.stream().noneMatch(item -> repeat('旧', 700).equals(item.getContent())));
        assertTrue(context.stream().anyMatch(item -> "最新问题".equals(item.getContent())));
    }

    /**
     * 注入会话记忆：活跃且非受限的记忆渲染为 system 块，插在系统提示之后。
     */
    @Test
    void injectsActiveSessionMemoryAfterSystemPrompt() {
        ConversationContextService memoryAwareService = new ConversationContextService(
                messageService, cacheService, summaryService, runService, toolCallLogService,
                sessionService, sessionMemoryService);
        agent.setId("agent-1");
        agent.setSystemPrompt("基础系统提示");
        AgentSession session = new AgentSession();
        session.setId("session-1");
        when(sessionService.getOrCreate("conversation-1", "user-1", "agent-1")).thenReturn(session);
        AgentSessionMemory memory = new AgentSessionMemory();
        memory.setSessionId("session-1");
        memory.setMemoryType("GOAL");
        memory.setContent("项目使用 Java 8");
        memory.setImportance(90);
        memory.setSensitivityLevel("NORMAL");
        memory.setStatus(AgentSessionMemory.STATUS_ACTIVE);
        when(sessionMemoryService.listInjectableForModel("session-1", 6))
                .thenReturn(Collections.singletonList(memory));
        List<AgentMessage> history = messagesAscending(1, 2);
        when(messageService.count(any())).thenReturn(2L);
        when(messageService.list(any())).thenReturn(history);

        List<ModelChatMessage> context = memoryAwareService.buildWithSummary(agent, provider, "conversation-1");
        memoryAwareService.injectSessionMemory(context, "conversation-1", "user-1", "agent-1");

        assertEquals("system", context.get(1).getRole());
        assertTrue(context.get(1).getContent().contains("【会话记忆】"));
        assertTrue(context.get(1).getContent().contains("项目使用 Java 8"));
    }

    /**
     * 无会话服务时注入为空操作，保持既有行为。
     */
    @Test
    void injectSessionMemoryIsNoOpWhenServicesAbsent() {
        List<ModelChatMessage> context = new ArrayList<ModelChatMessage>();
        context.add(new ModelChatMessage("system", "基础系统提示"));
        context.add(new ModelChatMessage("user", "历史消息"));

        service.injectSessionMemory(context, "conversation-1", "user-1", "agent-1");

        assertEquals(2, context.size());
        assertEquals("user", context.get(1).getRole());
    }

    /**
     * 处理messagesAscending。
     */
    private List<AgentMessage> messagesAscending(int start, int end) {
        List<AgentMessage> messages = new ArrayList<AgentMessage>();
        for (int i = start; i <= end; i++) {
            messages.add(message(i));
        }
        return messages;
    }

    /**
     * 处理messagesDescending。
     */
    private List<AgentMessage> messagesDescending(int start, int end) {
        List<AgentMessage> messages = messagesAscending(start, end);
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 消息当前请求。
     */
    private AgentMessage message(int index) {
        AgentMessage message = new AgentMessage();
        message.setId(String.format("%05d", index));
        message.setCreatedAt((long) index);
        message.setRole(index % 2 == 0 ? "user" : "assistant");
        message.setContent("message-" + index);
        message.setDeleted(false);
        return message;
    }

    /**
     * 处理repeat。
     */
    private String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }
}
