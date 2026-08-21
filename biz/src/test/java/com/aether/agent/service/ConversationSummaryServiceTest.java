package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentConversationSummary;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.entity.AgentSession;
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
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
    @Mock
    private ModelCatalogService modelCatalogService;
    @Mock
    private AgentConversationSummaryService conversationSummaryStore;
    @Mock
    private ContextMetricService contextMetricService;
    @Mock
    private AgentSessionService sessionService;

    private ConversationSummaryService service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(valueOperations.setIfAbsent(
                anyString(), any(), eq(5L), eq(TimeUnit.MINUTES))).thenReturn(true);
        lenient().when(conversationService.update(any(Wrapper.class))).thenReturn(true);
        lenient().when(conversationSummaryStore.save(any(AgentConversationSummary.class))).thenReturn(true);
        lenient().when(conversationSummaryStore.update(any(Wrapper.class))).thenReturn(true);
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService, modelCatalogService,
                conversationSummaryStore, contextMetricService, sessionService, null);
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
        AgentSession session = new AgentSession();
        session.setMemoryVersion(7);
        AgentMessage message = new AgentMessage();
        message.setId("00021");
        message.setCreatedAt(21L);
        message.setRole("user");
        message.setContent("企业版呢？");
        message.setRewrittenContent("企业版产品的退款期限是多少？");

        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("目标: 了解企业版退款期限", "msg-00022", "SENSITIVE"));
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("preliminary-1");
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                eq(agent), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);
        when(sessionService.getOne(any(Wrapper.class))).thenReturn(session);

        service.refreshAsync("conversation-1", null,
                completedGroup(message), agent, provider);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertTrue(stored.getSummary().contains("目标: 了解企业版退款期限"));
        assertTrue(stored.getContentJson().contains("confirmedFacts"));
        assertEquals("00022", stored.getCoveredUntilMessageId());
        assertEquals(22L, stored.getCoveredUntilCreatedAt());
        verify(conversationService, timeout(1000)).update(any(Wrapper.class));
        ArgumentCaptor<AgentConversationSummary> summaryCaptor =
                ArgumentCaptor.forClass(AgentConversationSummary.class);
        verify(conversationSummaryStore, timeout(1000)).save(summaryCaptor.capture());
        assertEquals(7, summaryCaptor.getValue().getSourceMemoryVersion());
        assertEquals("00021:00022", summaryCaptor.getValue().getSourceEventRange());
        assertEquals("SENSITIVE", summaryCaptor.getValue().getSourceSensitivityMax());
        ArgumentCaptor<ModelChatRequest> requestCaptor = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient, timeout(1000)).chat(requestCaptor.capture());
        String prompt = requestCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("企业版产品的退款期限是多少？"));
        org.junit.jupiter.api.Assertions.assertFalse(prompt.contains("企业版呢？"));
        verify(contextMetricService, timeout(1000)).recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                eq(agent), eq(provider));
        verify(contextMetricService, timeout(1000)).recordFinal(eq(preliminary), eq(null), eq("SYNC_COMPLETED"));
    }

    /**
     * 配置压缩模型时，摘要调用使用专用模型连接，不影响回答模型。
     */
    @Test
    void refreshUsesConfiguredCompressionModel() {
        AgentDefinition agent = new AgentDefinition();
        agent.setContextCompressionModelId("compression-model-1");
        ModelProvider chatProvider = new ModelProvider();
        chatProvider.setDefaultModel("chat-model");
        ModelProvider compressionProvider = new ModelProvider();
        compressionProvider.setDefaultModel("summary-model");
        AgentMessage message = message("00030", 30L);

        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("压缩摘要", "msg-00030"));
        when(modelCatalogService.resolveProvider("compression-model-1", "CHAT,MULTIMODAL"))
                .thenReturn(compressionProvider);
        when(modelClientFactory.getClient(compressionProvider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message), agent, chatProvider);

        ArgumentCaptor<ModelChatRequest> requestCaptor = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient, timeout(1000)).chat(requestCaptor.capture());
        assertEquals(compressionProvider, requestCaptor.getValue().getProvider());
        assertEquals("summary-model", requestCaptor.getValue().getModel());
        verify(modelClientFactory, never()).getClient(chatProvider);
    }

    /**
     * 无效结构化摘要不会替换现有摘要。
     */
    @Test
    void invalidStructuredSummaryDoesNotPersist() {
        ModelProvider provider = new ModelProvider();
        AgentMessage message = message("00031", 31L);
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("plain text summary is no longer accepted");
        response.setPromptTokens(17);
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("preliminary-invalid");
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(valueOperations, after(500).never()).set(
                eq("agent:summary:v3:conversation-1"), any(),
                eq(24L), eq(TimeUnit.HOURS));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(17), eq("FAILED_FALLBACK"));
    }

    /**
     * 重复条目ID的结构化摘要不会被持久化。
     */
    @Test
    void duplicateSummaryItemIdsDoNotPersist() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[{\"id\":\"dup\",\"content\":\"目标\",\"sourceEventIds\":[\"00032\"],\"sensitivityLevel\":\"NORMAL\"}],"
                + "\"constraints\":[{\"id\":\"dup\",\"content\":\"约束\",\"sourceEventIds\":[\"00032\"],\"sensitivityLevel\":\"NORMAL\"}],"
                + "\"confirmedFacts\":[],\"decisions\":[],\"openQuestions\":[],\"pendingActions\":[],\"artifacts\":[]"
                + "}");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00032", 32L)), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 摘要结果包含密钥内容时不会被持久化。
     */
    @Test
    void forbiddenSensitiveSummaryContentDoesNotPersist() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("api_key=super-secret-value", "00033"));
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00033", 33L)), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(valueOperations, after(500).never()).set(
                eq("agent:summary:v3:conversation-1"), any(),
                eq(24L), eq(TimeUnit.HOURS));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 产物摘要项可仅使用name/reference，不强制要求content。
     */
    @Test
    void artifactSummaryWithoutContentPersists() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[],\"constraints\":[],\"confirmedFacts\":[],\"decisions\":[],"
                + "\"openQuestions\":[],\"pendingActions\":[],"
                + "\"artifacts\":[{\"id\":\"artifact-1\",\"name\":\"设计文档\","
                + "\"reference\":\"doc://design\",\"sourceEventIds\":[\"00034\"],"
                + "\"sensitivityLevel\":\"NORMAL\"}]"
                + "}");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00034", 34L)), new AgentDefinition(), provider);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertTrue(stored.getSummary().contains("设计文档"));
        verify(conversationSummaryStore, timeout(1000)).save(any(AgentConversationSummary.class));
    }

    /**
     * 摘要条目可仅追溯到来源记忆。
     */
    @Test
    void summaryItemWithOnlySourceMemoryPersists() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[{\"id\":\"goal-1\",\"content\":\"保持 Java 8 兼容\","
                + "\"sourceMemoryIds\":[\"memory-1\"],\"sensitivityLevel\":\"NORMAL\"}],"
                + "\"constraints\":[],\"confirmedFacts\":[],\"decisions\":[],"
                + "\"openQuestions\":[],\"pendingActions\":[],\"artifacts\":[]"
                + "}");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00035", 35L)), new AgentDefinition(), provider);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertTrue(stored.getContentJson().contains("memory-1"));
        verify(conversationSummaryStore, timeout(1000)).save(any(AgentConversationSummary.class));
    }

    /**
     * 待确认问题可暂时没有来源指针。
     */
    @Test
    void openQuestionWithoutSourcePersists() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[],\"constraints\":[],\"confirmedFacts\":[],\"decisions\":[],"
                + "\"openQuestions\":[{\"id\":\"question-1\",\"content\":\"是否需要移动端适配？\","
                + "\"sensitivityLevel\":\"NORMAL\"}],"
                + "\"pendingActions\":[],\"artifacts\":[]"
                + "}");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00036", 36L)), new AgentDefinition(), provider);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertTrue(stored.getSummary().contains("是否需要移动端适配"));
        verify(conversationSummaryStore, timeout(1000)).save(any(AgentConversationSummary.class));
    }

    /**
     * 敏感级别会去除前后空白后校验。
     */
    @Test
    void sensitivityLevelWhitespaceIsTrimmed() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("需要安全审计", "00037", " SENSITIVE "));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00037", 37L)), new AgentDefinition(), provider);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertTrue(stored.getContentJson().contains("\"sensitivityLevel\":\"SENSITIVE\""));
        verify(conversationSummaryStore, timeout(1000)).save(any(AgentConversationSummary.class));
    }

    /**
     * 过长来源ID不会被截断后持久化。
     */
    @Test
    void overlongSourceIdDoesNotPersist() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[{\"id\":\"goal-1\",\"content\":\"目标\","
                + "\"sourceEventIds\":[\"source-id-that-is-way-too-long-for-a-trace-pointer-000000000000000000000\"],"
                + "\"sensitivityLevel\":\"NORMAL\"}],"
                + "\"constraints\":[],\"confirmedFacts\":[],\"decisions\":[],"
                + "\"openQuestions\":[],\"pendingActions\":[],\"artifacts\":[]"
                + "}");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00037", 37L)), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 过长摘要正文不会被截断后持久化。
     */
    @Test
    void overlongSummaryContentDoesNotPersist() {
        ModelProvider provider = new ModelProvider();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            content.append('a');
        }
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson(content.toString(), "00038"));
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00038", 38L)), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 过长摘要ID不会被截断后持久化。
     */
    @Test
    void overlongSummaryItemIdDoesNotPersist() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[{\"id\":\"summary-item-id-that-is-longer-than-sixty-four-characters-000000000000000000\","
                + "\"content\":\"目标\",\"sourceEventIds\":[\"00039\"],\"sensitivityLevel\":\"NORMAL\"}],"
                + "\"constraints\":[],\"confirmedFacts\":[],\"decisions\":[],"
                + "\"openQuestions\":[],\"pendingActions\":[],\"artifacts\":[]"
                + "}");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00039", 39L)), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 摘要条目未知字段不会被静默忽略。
     */
    @Test
    void unknownSummaryItemFieldDoesNotPersist() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{"
                + "\"goals\":[{\"id\":\"goal-1\",\"content\":\"目标\",\"sourceEventIds\":[\"00040\"],"
                + "\"sensitivityLevel\":\"NORMAL\",\"unexpected\":\"value\"}],"
                + "\"constraints\":[],\"confirmedFacts\":[],\"decisions\":[],"
                + "\"openQuestions\":[],\"pendingActions\":[],\"artifacts\":[]"
                + "}");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                any(AgentDefinition.class), eq(provider))).thenReturn(preliminary);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00040", 40L)), new AgentDefinition(), provider);

        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 高风险密钥内容不会出站到压缩模型，并记录失败回退指标。
     */
    @Test
    void outboundGovernanceBlocksHighRiskSecretsBeforeCompressionCall() {
        service.shutdown();
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService, modelCatalogService,
                conversationSummaryStore, contextMetricService,
                new CompressionOutboundGovernanceService(true));
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        AgentMessage message = message("00040", 40L);
        message.setContent("api_key=super-secret-value");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("preliminary-blocked");
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                eq(agent), eq(provider))).thenReturn(preliminary);

        service.refreshAsync("conversation-1", null,
                completedGroup(message), agent, provider);

        verify(modelClientFactory, after(500).never()).getClient(any());
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
    }

    /**
     * 未授权压缩出站的供应商不会收到摘要请求。
     */
    @Test
    void outboundGovernanceBlocksProvidersWithoutCompressionPermission() {
        service.shutdown();
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService, modelCatalogService,
                conversationSummaryStore, contextMetricService,
                new CompressionOutboundGovernanceService(true));
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        provider.setCompressionOutboundAllowed(false);
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("preliminary-provider-blocked");
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                eq(agent), eq(provider))).thenReturn(preliminary);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00042", 42L)), agent, provider);

        verify(modelClientFactory, after(500).never()).getClient(any());
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
    }

    /**
     * 结构化策略不允许当前处理区域时阻断压缩出站。
     */
    @Test
    void outboundGovernanceBlocksProviderRegionOutsidePolicy() {
        service.shutdown();
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService, modelCatalogService,
                conversationSummaryStore, contextMetricService,
                new CompressionOutboundGovernanceService(true));
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        provider.setCompressionOutboundAllowed(true);
        provider.setProcessingRegion("US");
        provider.setDataProcessingPolicy("{\"allowCompressionOutbound\":true,\"allowedRegions\":[\"CN\"],\"allowTraining\":false}");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("preliminary-region-blocked");
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                eq(agent), eq(provider))).thenReturn(preliminary);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00043", 43L)), agent, provider);

        verify(modelClientFactory, after(500).never()).getClient(any());
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 结构化策略允许训练复用时阻断压缩出站。
     */
    @Test
    void outboundGovernanceBlocksProviderPolicyAllowingTraining() {
        service.shutdown();
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService, modelCatalogService,
                conversationSummaryStore, contextMetricService,
                new CompressionOutboundGovernanceService(true));
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        provider.setCompressionOutboundAllowed(true);
        provider.setProcessingRegion("CN");
        provider.setDataProcessingPolicy("{\"allowCompressionOutbound\":true,\"allowedRegions\":[\"CN\"],\"allowTraining\":true}");
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("preliminary-training-blocked");
        when(contextMetricService.recordPreliminary(anyString(), eq(1),
                eq("COMPRESSION"), eq("ASYNC_PENDING"), any(), nullable(java.util.List.class),
                eq(agent), eq(provider))).thenReturn(preliminary);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00044", 44L)), agent, provider);

        verify(modelClientFactory, after(500).never()).getClient(any());
        verify(contextMetricService, timeout(1000)).recordFinal(
                eq(preliminary), eq(null), eq("FAILED_FALLBACK"));
    }

    /**
     * 普通PII会在压缩出站前脱敏。
     */
    @Test
    void outboundGovernanceRedactsPiiBeforeCompressionCall() {
        service.shutdown();
        service = new ConversationSummaryService(
                redisTemplate, modelClientFactory, conversationService, modelCatalogService,
                conversationSummaryStore, contextMetricService,
                new CompressionOutboundGovernanceService(true));
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        provider.setCompressionOutboundAllowed(true);
        provider.setProcessingRegion("CN");
        provider.setDataProcessingPolicy("{\"allowCompressionOutbound\":true,\"allowedRegions\":[\"CN\"],\"allowTraining\":false}");
        AgentMessage message = message("00041", 41L);
        message.setContent("联系 test@example.com 跟进");
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("联系信息已脱敏", "00041"));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        service.refreshAsync("conversation-1", null,
                completedGroup(message), agent, provider);

        ArgumentCaptor<ModelChatRequest> requestCaptor = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient, timeout(1000)).chat(requestCaptor.capture());
        String prompt = requestCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("[REDACTED_CONTACT]"));
        assertFalse(prompt.contains("test@example.com"));
    }

    /**
     * 未完成工具调用事件组不会被压缩。
     */
    @Test
    void pendingToolCallGroupDoesNotCompress() {
        AgentMessage user = message("00050", 50L);
        AgentMessage toolCall = assistant("00051", 51L, "调用查询工具");
        toolCall.setToolCalls("[{\"id\":\"call-1\",\"type\":\"function\"}]");

        service.refreshAsync("conversation-1", null,
                Arrays.asList(user, toolCall), new AgentDefinition(), new ModelProvider());

        verify(modelClientFactory, after(500).never()).getClient(any());
        verify(conversationSummaryStore, after(500).never()).save(any(AgentConversationSummary.class));
    }

    /**
     * 只压缩完整事件组前缀，保留末尾未完成用户输入。
     */
    @Test
    void compressesOnlyCompletedEventGroupPrefix() {
        AgentDefinition agent = new AgentDefinition();
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("已完成第一轮", "00061"));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);
        AgentMessage pending = message("00062", 62L);
        pending.setContent("pending second turn");

        service.refreshAsync("conversation-1", null,
                Arrays.asList(
                        message("00060", 60L),
                        assistant("00061", 61L, "第一轮答复"),
                        pending),
                agent, provider);

        ArgumentCaptor<ModelChatRequest> requestCaptor = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient, timeout(1000)).chat(requestCaptor.capture());
        String prompt = requestCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("第一轮答复"));
        assertFalse(prompt.contains("pending second turn"));
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), valueCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SummarySnapshot stored = JSON.parseObject(valueCaptor.getValue().toString(), SummarySnapshot.class);
        assertEquals("00061", stored.getCoveredUntilMessageId());
        assertEquals(61L, stored.getCoveredUntilCreatedAt());
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
     * 结构化摘要来源记忆版本落后时，不再渲染旧摘要。
     */
    @Test
    void staleStructuredSummaryMemoryVersionDoesNotRender() {
        AgentConversationSummary summary = new AgentConversationSummary();
        summary.setConversationId("conversation-1");
        summary.setContentJson(summaryJson("旧记忆事实", "00020"));
        summary.setCoveredUntilMessageId("00020");
        summary.setCoveredUntilCreatedAt(20L);
        summary.setSourceMemoryVersion(2);
        summary.setStatus(AgentConversationSummary.STATUS_READY);
        AgentSession session = new AgentSession();
        session.setMemoryVersion(3);
        when(conversationSummaryStore.getOne(any(Wrapper.class))).thenReturn(summary);
        when(sessionService.getOne(any(Wrapper.class))).thenReturn(session);

        SummarySnapshot result = service.get("conversation-1");

        assertNull(result);
        verify(redisTemplate).delete("agent:summary:v3:conversation-1");
        verify(conversationSummaryStore).update(any(Wrapper.class));
    }

    /**
     * 处理distributedLockPreventsDuplicateRefresh。
     */
    @Test
    void distributedLockPreventsDuplicateRefresh() {
        when(valueOperations.setIfAbsent(
                anyString(), any(), eq(5L), eq(TimeUnit.MINUTES))).thenReturn(false);

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00021", 21L)), new AgentDefinition(),
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
                completedGroup(message("00021", 21L)), new AgentDefinition(),
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
        response.setContent(summaryJson("must not be stored", "msg-00021"));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenAnswer(invocation -> {
            generationStarted.countDown();
            assertTrue(allowGenerationToFinish.await(1, TimeUnit.SECONDS));
            return response;
        });

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00021", 21L)),
                new AgentDefinition(), provider);
        assertTrue(generationStarted.await(1, TimeUnit.SECONDS));

        service.evict("conversation-1");
        allowGenerationToFinish.countDown();

        verify(valueOperations, after(500).never()).set(
                eq("agent:summary:v3:conversation-1"), any(),
                eq(24L), eq(TimeUnit.HOURS));
    }

    /**
     * 失效后的新刷新可以重建摘要。
     */
    @Test
    void refreshAfterEvictionCanRebuildSummary() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(summaryJson("rebuilt summary", "00023"));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);
        service.evict("conversation-1");

        service.refreshAsync("conversation-1", null,
                completedGroup(message("00022", 22L)),
                new AgentDefinition(), provider);

        verify(valueOperations, timeout(1000)).set(
                eq("agent:summary:v3:conversation-1"), any(),
                eq(24L), eq(TimeUnit.HOURS));
        verify(conversationSummaryStore, timeout(1000)).save(any(AgentConversationSummary.class));
    }

    /**
     * 相同refreshId的结构化摘要重试不会重复更新当前摘要。
     */
    @Test
    void duplicateRefreshIdDoesNotRewriteStructuredSummary() {
        AgentConversationSummary existing = new AgentConversationSummary();
        existing.setId("summary-1");
        existing.setConversationId("conversation-1");
        existing.setContentJson(summaryJson("existing", "00021"));
        existing.setCoveredUntilMessageId("00021");
        existing.setCoveredUntilCreatedAt(21L);
        existing.setSummaryVersion(3);
        existing.setRefreshId("refresh-1");
        existing.setStatus(AgentConversationSummary.STATUS_READY);
        when(conversationSummaryStore.getOne(any(Wrapper.class))).thenReturn(existing);

        SummarySnapshot snapshot = new SummarySnapshot();
        snapshot.setContentJson(summaryJson("existing", "00021"));
        snapshot.setCoveredUntilMessageId("00021");
        snapshot.setCoveredUntilCreatedAt(21L);
        snapshot.setRefreshId("refresh-1");

        Boolean saved = ReflectionTestUtils.invokeMethod(
                service, "persistStructuredSnapshot", "conversation-1", snapshot);

        assertTrue(Boolean.TRUE.equals(saved));
        verify(conversationSummaryStore, never()).update(any(Wrapper.class));
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

    /**
     * 完整用户-助手事件组。
     */
    private List<AgentMessage> completedGroup(AgentMessage user) {
        long userCreatedAt = user.getCreatedAt() == null ? 0L : user.getCreatedAt();
        return Arrays.asList(user, assistant(nextId(user.getId()), userCreatedAt + 1, "ack"));
    }

    /**
     * 创建助手消息。
     */
    private AgentMessage assistant(String id, long createdAt, String content) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setCreatedAt(createdAt);
        message.setRole("assistant");
        message.setContent(content);
        return message;
    }

    /**
     * 生成顺序测试ID。
     */
    private String nextId(String id) {
        try {
            return String.format("%05d", Integer.parseInt(id) + 1);
        } catch (Exception e) {
            return id + "-assistant";
        }
    }

    /**
     * 创建测试用结构化摘要JSON。
     */
    private String summaryJson(String content, String eventId) {
        return summaryJson(content, eventId, "NORMAL");
    }

    /**
     * 创建测试用结构化摘要JSON。
     */
    private String summaryJson(String content, String eventId, String sensitivityLevel) {
        return "{"
                + "\"goals\":[],"
                + "\"constraints\":[],"
                + "\"confirmedFacts\":[{\"id\":\"fact-1\",\"content\":\"" + content
                + "\",\"sourceEventIds\":[\"" + eventId + "\"],\"sensitivityLevel\":\"" + sensitivityLevel + "\"}],"
                + "\"decisions\":[],"
                + "\"openQuestions\":[],"
                + "\"pendingActions\":[],"
                + "\"artifacts\":[]"
                + "}";
    }
}
