package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.ConversationSummaryService.SummarySnapshot;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证会话Summary服务的行为。
 */
@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ModelClientFactory modelClientFactory;
    @Mock
    private ModelClient modelClient;
    @Mock
    private AgentConversationService conversationService;

    private ConversationSummaryService service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(
                anyString(), any(), eq(5L), eq(TimeUnit.MINUTES))).thenReturn(true);
        lenient().when(conversationService.update(any(Wrapper.class))).thenReturn(true);
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService);
    }

    /**
     * 处理tearDown。
     */
    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    /**
     * 处理readsOnlySummaryWithCoverageCursor。
     */
    @Test
    void readsOnlySummaryWithCoverageCursor() {
        SummarySnapshot stored = new SummarySnapshot();
        stored.setSummary("summary");
        stored.setCoveredUntilMessageId("00020");
        stored.setCoveredUntilCreatedAt(20L);
        when(valueOperations.get("agent:summary:v3:conversation-1"))
                .thenReturn(JSON.toJSONString(stored));

        SummarySnapshot result = service.get("conversation-1");

        assertEquals("summary", result.getSummary());
        assertEquals("00020", result.getCoveredUntilMessageId());
        assertEquals(20L, result.getCoveredUntilCreatedAt());
    }

    /**
     * 处理rejectsLegacySummaryWithoutCoverageCursor。
     */
    @Test
    void rejectsLegacySummaryWithoutCoverageCursor() {
        when(valueOperations.get("agent:summary:v3:conversation-1"))
                .thenReturn("{\"summary\":\"legacy\"}");

        assertNull(service.get("conversation-1"));
    }

    /**
     * 处理refreshPersistsLastCovered消息Cursor。
     */
    @Test
    void refreshPersistsLastCoveredMessageCursor() {
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        AgentMessage message = new AgentMessage();
        message.setId("00021");
        message.setCreatedAt(21L);
        message.setRole("user");
        message.setContent("企业版呢？");
        message.setRewrittenContent("企业版产品的退款期限是多少？");

        ModelChatResponse response = new ModelChatResponse();
        response.setContent("updated summary");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                Collections.singletonList(message), agent, provider);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertEquals("updated summary", stored.getSummary());
        assertEquals("00021", stored.getCoveredUntilMessageId());
        assertEquals(21L, stored.getCoveredUntilCreatedAt());
        verify(conversationService, timeout(1000)).update(any(Wrapper.class));
        ArgumentCaptor<ModelChatRequest> requestCaptor = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient, timeout(1000)).chat(requestCaptor.capture());
        String prompt = requestCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("企业版产品的退款期限是多少？"));
        org.junit.jupiter.api.Assertions.assertFalse(prompt.contains("企业版呢？"));
    }

    /**
     * 缓存MissLoadsPersistentSummaryAndBackfillsRedis。
     */
    @Test
    void cacheMissLoadsPersistentSummaryAndBackfillsRedis() {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setSummary("persistent summary");
        conversation.setSummaryCoveredMessageId("00020");
        conversation.setSummaryCoveredCreatedAt(20L);
        conversation.setSummaryUpdatedAt(30L);
        when(conversationService.getById("conversation-1")).thenReturn(conversation);

        SummarySnapshot result = service.get("conversation-1");

        assertEquals("persistent summary", result.getSummary());
        assertEquals("00020", result.getCoveredUntilMessageId());
        verify(valueOperations).set(
                eq("agent:summary:v3:conversation-1"), any(),
                eq(24L), eq(TimeUnit.HOURS));
    }

    /**
     * 处理distributedLockPreventsDuplicateRefresh。
     */
    @Test
    void distributedLockPreventsDuplicateRefresh() {
        when(valueOperations.setIfAbsent(
                anyString(), any(), eq(5L), eq(TimeUnit.MINUTES))).thenReturn(false);

        service.refreshAsync("conversation-1", null,
                Collections.singletonList(message("00021", 21L)), new AgentDefinition(),
                new ModelProvider());

        verify(modelClientFactory, after(300).never()).getClient(any());
    }

    /**
     * 处理newerCursorPreventsStaleSummaryGeneration。
     */
    @Test
    void newerCursorPreventsStaleSummaryGeneration() {
        SummarySnapshot newer = new SummarySnapshot();
        newer.setSummary("newer");
        newer.setCoveredUntilMessageId("00022");
        newer.setCoveredUntilCreatedAt(22L);
        when(valueOperations.get("agent:summary:v3:conversation-1"))
                .thenReturn(JSON.toJSONString(newer));

        service.refreshAsync("conversation-1", null,
                Collections.singletonList(message("00021", 21L)), new AgentDefinition(),
                new ModelProvider());

        verify(modelClientFactory, after(300).never()).getClient(any());
    }

    /**
     * 处理deletionDuringGenerationPreventsSummaryFromBeingWrittenBack。
     */
    @Test
    void deletionDuringGenerationPreventsSummaryFromBeingWrittenBack() throws Exception {
        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch allowGenerationToFinish = new CountDownLatch(1);
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("must not be stored");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenAnswer(invocation -> {
            generationStarted.countDown();
            assertTrue(allowGenerationToFinish.await(1, TimeUnit.SECONDS));
            return response;
        });

        service.refreshAsync("conversation-1", null,
                Collections.singletonList(message("00021", 21L)),
                new AgentDefinition(), provider);
        assertTrue(generationStarted.await(1, TimeUnit.SECONDS));

        service.evict("conversation-1");
        allowGenerationToFinish.countDown();

        verify(valueOperations, after(500).never()).set(
                eq("agent:summary:v3:conversation-1"), any(),
                eq(24L), eq(TimeUnit.HOURS));
    }

    /**
     * 消息当前请求。
     */
    private AgentMessage message(String id, long createdAt) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setCreatedAt(createdAt);
        message.setRole("user");
        message.setContent("new fact");
        return message;
    }
}
