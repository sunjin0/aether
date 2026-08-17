package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.AgentMessageMapper;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.impl.PreferenceReasoningEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证管理员偏好Extraction服务实现的行为。
 */
@ExtendWith(MockitoExtension.class)
class AdminPreferenceExtractionServiceImplTest {

    @Mock
    private AdminPreferenceMapper preferenceMapper;
    @Mock
    private AgentMessageMapper messageMapper;
    @Mock
    private AdminPreferenceEventService eventService;
    @Mock
    private ModelClientFactory modelClientFactory;
    @Mock
    private ModelClient modelClient;
    @Mock
    private PreferenceReasoningEngine reasoningEngine;

    private AdminPreferenceExtractionServiceImpl service;
    private AgentDefinition agent;
    private ModelProvider provider;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() throws Exception {
        service = new AdminPreferenceExtractionServiceImpl();
        setField("preferenceMapper", preferenceMapper);
        setField("messageMapper", messageMapper);
        setField("eventService", eventService);
        setField("modelClientFactory", modelClientFactory);
        setField("reasoningEngine", reasoningEngine);
        agent = new AgentDefinition();
        provider = new ModelProvider();
    }

    /**
     * 处理ordinary会话判断是否为MarkedProcessedWithoutCalling模型。
     */
    @Test
    void ordinaryConversationIsMarkedProcessedWithoutCallingModel() {
        AgentMessage user = message("00001", 1L, "user", "解释一下这个方法");
        AgentMessage assistant = message("00002", 2L, "assistant", "这个方法负责组装返回值");
        when(eventService.getLastEvent(
                "user-1", AdminPreferenceEvent.EVENT_EXTRACTION_MARKER, "conversation-1"))
                .thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(Arrays.asList(user, assistant));

        service.extractAsync("user-1", "conversation-1", user, assistant, agent, provider);

        verify(modelClientFactory, never()).getClient(any());
        ArgumentCaptor<AdminPreferenceEvent> eventCaptor =
                ArgumentCaptor.forClass(AdminPreferenceEvent.class);
        verify(eventService).logEvent(eventCaptor.capture());
        assertEquals(AdminPreferenceEvent.EVENT_EXTRACTION_MARKER,
                eventCaptor.getValue().getEventType());
        assertEquals("00002", eventCaptor.getValue().getMessageId());
        assertEquals("conversation-1", eventCaptor.getValue().getConversationId());
    }

    /**
     * 处理explicit偏好UsesCanonicalIdentityAndInvalidates缓存。
     */
    @Test
    void explicitPreferenceUsesCanonicalIdentityAndInvalidatesCache() {
        AgentMessage user = message("00003", 3L, "user", "企业版呢？");
        user.setRewrittenContent("以后总是用中文回答");
        AgentMessage assistant = message("00004", 4L, "assistant", "好的");
        when(eventService.getLastEvent(
                "user-1", AdminPreferenceEvent.EVENT_EXTRACTION_MARKER, "conversation-1"))
                .thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(Arrays.asList(user, assistant));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{\"summary\":\"The user wants Chinese responses.\",\"preferences\":[{"
                + "\"category\":\"language\",\"key_name\":\"preferred_language\","
                + "\"value\":\"zh-CN\",\"confidence\":0.9}]}");
        when(modelClient.chat(any())).thenReturn(response);
        when(preferenceMapper.selectByIdentity(
                "user-1", "language", "preferred_language", "global", ""))
                .thenReturn(null);

        service.extractAsync("user-1", "conversation-1", user, assistant, agent, provider);

        ArgumentCaptor<AdminPreference> preferenceCaptor =
                ArgumentCaptor.forClass(AdminPreference.class);
        verify(preferenceMapper).insert(preferenceCaptor.capture());
        assertEquals("global", preferenceCaptor.getValue().getScope());
        assertEquals("", preferenceCaptor.getValue().getScopeDetail());
        verify(reasoningEngine).clearUserCache("user-1");
        verify(eventService).getLastEvent(
                eq("user-1"), eq(AdminPreferenceEvent.EVENT_EXTRACTION_MARKER),
                eq("conversation-1"));
    }

    /**
     * 处理repeated偏好Records会话Evidence。
     */
    @Test
    void repeatedPreferenceRecordsConversationEvidence() {
        AgentMessage user = message("00005", 5L, "user", "以后总是用中文回答");
        AgentMessage assistant = message("00006", 6L, "assistant", "好的");
        when(eventService.getLastEvent(
                "user-1", AdminPreferenceEvent.EVENT_EXTRACTION_MARKER, "conversation-1"))
                .thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(Arrays.asList(user, assistant));
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("[{\"category\":\"language\",\"key_name\":\"preferred_language\","
                + "\"value\":\"zh-CN\",\"confidence\":0.9}]");
        when(modelClient.chat(any())).thenReturn(response);
        AdminPreference existing = new AdminPreference();
        existing.setId("preference-1");
        existing.setAdminId("user-1");
        existing.setCategory("language");
        existing.setKeyName("preferred_language");
        existing.setValue("zh-CN");
        existing.setConfidence(new java.math.BigDecimal("0.80"));
        existing.setUsageCount(1);
        when(preferenceMapper.selectByIdentity(
                "user-1", "language", "preferred_language", "global", ""))
                .thenReturn(existing);

        service.extractAsync("user-1", "conversation-1", user, assistant, agent, provider);

        ArgumentCaptor<AdminPreferenceEvent> events =
                ArgumentCaptor.forClass(AdminPreferenceEvent.class);
        verify(eventService, atLeastOnce()).logEvent(events.capture());
        List<AdminPreferenceEvent> values = events.getAllValues();
        AdminPreferenceEvent evidence = values.stream()
                .filter(event -> AdminPreferenceEvent.EVENT_EXTRACT.equals(event.getEventType()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("preference-1", evidence.getPreferenceId());
        assertEquals("conversation-1", evidence.getConversationId());
    }

    /**
     * 消息当前请求。
     */
    private AgentMessage message(String id, Long createdAt, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setCreatedAt(createdAt);
        message.setConversationId("conversation-1");
        message.setMessageType("chat");
        message.setRole(role);
        message.setContent(content);
        message.setDeleted(false);
        return message;
    }

    /**
     * 处理setField。
     */
    private void setField(String name, Object value) throws Exception {
        Field field = AdminPreferenceExtractionServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
