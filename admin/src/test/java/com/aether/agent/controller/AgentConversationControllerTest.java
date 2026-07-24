package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.ConversationCacheService;
import com.aether.agent.service.ConversationSummaryService;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConversationControllerTest {

    @Mock private AgentConversationService conversationService;
    @Mock private AgentMessageService messageService;
    @Mock private AgentRunService runService;
    @Mock private AgentToolCallLogService toolCallLogService;
    @Mock private AdminPreferenceEventService preferenceEventService;
    @Mock private AdminPreferenceService preferenceService;
    @Mock private ConversationCacheService cacheService;
    @Mock private ConversationSummaryService summaryService;
    @Mock private I18nService i18nService;

    private AgentConversationController controller;

    @BeforeEach
    void setUp() {
        initTableInfo(AgentConversation.class);
        initTableInfo(AgentMessage.class);
        initTableInfo(AgentRun.class);
        initTableInfo(AgentToolCallLog.class);
        initTableInfo(AdminPreferenceEvent.class);
        new I18nUtils(i18nService);
        when(i18nService.getMessage(any(String.class))).thenReturn("ok");

        HashMap<String, String> user = new HashMap<String, String>();
        user.put("userId", "user-1");
        CurrentUser.set(user);
        controller = new AgentConversationController(
                conversationService, messageService, runService, toolCallLogService,
                preferenceEventService, preferenceService, cacheService, summaryService);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

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

    private void initTableInfo(Class<?> type) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
    }
}
