package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.mapper.AgentConversationMapper;
import com.aether.agent.mapper.AgentMessageMapper;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossConversationPatternServiceTest {

    @Mock private AgentConversationMapper conversationMapper;
    @Mock private AgentMessageMapper messageMapper;
    @Mock private AdminPreferenceMapper preferenceMapper;
    @Mock private AdminPreferenceEventService eventService;
    @Mock private PreferenceReasoningEngine reasoningEngine;

    private CrossConversationPatternService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CrossConversationPatternService();
        setField("conversationMapper", conversationMapper);
        setField("messageMapper", messageMapper);
        setField("preferenceMapper", preferenceMapper);
        setField("eventService", eventService);
        setField("reasoningEngine", reasoningEngine);
    }

    @Test
    void threeDistinctConversationsCreatePreferenceWithTraceableEvidence() {
        List<AgentConversation> conversations = Arrays.asList(
                conversation("c1"), conversation("c2"), conversation("c3"));
        when(conversationMapper.selectList(any())).thenReturn(conversations);
        when(messageMapper.selectList(any()))
                .thenReturn(Collections.singletonList(message("m1", "c1")))
                .thenReturn(Collections.singletonList(message("m2", "c2")))
                .thenReturn(Collections.singletonList(message("m3", "c3")));
        when(preferenceMapper.selectByIdentity(
                "user-1", "language", "preferred_language", "global", ""))
                .thenReturn(null);
        doAnswer(invocation -> {
            AdminPreference preference = invocation.getArgument(0);
            preference.setId("preference-1");
            return 1;
        }).when(preferenceMapper).insert(any(AdminPreference.class));
        when(eventService.count(any())).thenReturn(0L);

        service.analyzeRecentPatterns();

        verify(preferenceMapper).insert(any(AdminPreference.class));
        ArgumentCaptor<AdminPreferenceEvent> events =
                ArgumentCaptor.forClass(AdminPreferenceEvent.class);
        verify(eventService, org.mockito.Mockito.times(3)).logEvent(events.capture());
        List<String> evidenceConversations = events.getAllValues().stream()
                .map(AdminPreferenceEvent::getConversationId)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("c1", "c2", "c3"), evidenceConversations);
    }

    @Test
    void repeatedSignalsInsideOneConversationAreNotCrossConversationEvidence() {
        when(conversationMapper.selectList(any()))
                .thenReturn(Collections.singletonList(conversation("c1")));
        when(messageMapper.selectList(any())).thenReturn(Arrays.asList(
                message("m1", "c1"), message("m2", "c1"), message("m3", "c1")));

        service.analyzeRecentPatterns();

        verify(preferenceMapper, never()).insert(any(AdminPreference.class));
        verify(eventService, never()).logEvent(any(AdminPreferenceEvent.class));
    }

    private AgentConversation conversation(String id) {
        AgentConversation conversation = new AgentConversation();
        conversation.setId(id);
        conversation.setUserId("user-1");
        conversation.setDeleted(false);
        return conversation;
    }

    private AgentMessage message(String id, String conversationId) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setMessageType("chat");
        message.setContent("请用中文回答");
        message.setDeleted(false);
        return message;
    }

    private void setField(String name, Object value) throws Exception {
        Field field = CrossConversationPatternService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
