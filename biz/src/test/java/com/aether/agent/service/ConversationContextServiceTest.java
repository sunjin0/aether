package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private ConversationContextService service;
    private AgentDefinition agent;
    private ModelProvider provider;

    @BeforeEach
    void setUp() {
        service = new ConversationContextService(messageService, cacheService, summaryService);
        agent = new AgentDefinition();
        provider = new ModelProvider();
    }

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

    @Test
    void persistedMessageInvalidatesRatherThanMutatesContextCache() {
        service.append("conversation-1", new ModelChatMessage("user", "latest"));

        verify(cacheService).evict("conversation-1");
    }

    @Test
    void restoresAuditedToolCallsBeforeTheirAssistantAnswer() {
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

        assertEquals(3, context.size());
        assertEquals("assistant", context.get(0).getRole());
        assertTrue(context.get(0).getToolCalls().contains("\"lookup\""));
        assertEquals("tool", context.get(1).getRole());
        assertEquals("call-1", context.get(1).getToolCallId());
        assertEquals("{\"name\":\"Aether\"}", context.get(1).getContent());
        assertEquals("message-1", context.get(2).getContent());
    }

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

    private List<AgentMessage> messagesAscending(int start, int end) {
        List<AgentMessage> messages = new ArrayList<AgentMessage>();
        for (int i = start; i <= end; i++) {
            messages.add(message(i));
        }
        return messages;
    }

    private List<AgentMessage> messagesDescending(int start, int end) {
        List<AgentMessage> messages = messagesAscending(start, end);
        Collections.reverse(messages);
        return messages;
    }

    private AgentMessage message(int index) {
        AgentMessage message = new AgentMessage();
        message.setId(String.format("%05d", index));
        message.setCreatedAt((long) index);
        message.setRole(index % 2 == 0 ? "user" : "assistant");
        message.setContent("message-" + index);
        message.setDeleted(false);
        return message;
    }

    private String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }
}
