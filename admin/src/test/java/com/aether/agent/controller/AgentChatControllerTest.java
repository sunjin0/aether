package com.aether.agent.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.ChatAttachmentService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChatControllerTest {

    @BeforeEach
    void setUpI18n() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", i18n);
    }

    @AfterEach
    void clearCurrentUser() {
        com.aether.local.CurrentUser.remove();
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", null);
    }

    @Test
    void chatReturnsNotFoundWhenAgentDoesNotExist() {
        AgentDefinitionService definitions = mock(AgentDefinitionService.class);
        when(definitions.getById("missing-agent")).thenReturn(null);
        AgentChatController controller = controller(definitions, config(600L));
        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId("missing-agent");

        ServerException error = assertThrows(ServerException.class, () -> controller.chat(dto));

        assertEquals(true, error.getMessage().startsWith("404:"));
    }

    @Test
    void deepStreamTimeoutUsesConfiguredRunTimeoutAndMargin() {
        AgentChatController controller = controller(mock(AgentDefinitionService.class), config(600L));

        Long timeout = ReflectionTestUtils.invokeMethod(controller, "deepStreamTimeoutMs");

        assertEquals(630000L, timeout);
    }

    @Test
    void deepStreamTimeoutFallsBackForNonPositiveConfiguration() {
        AgentChatController controller = controller(mock(AgentDefinitionService.class), config(0L));

        Long timeout = ReflectionTestUtils.invokeMethod(controller, "deepStreamTimeoutMs");

        assertEquals(630000L, timeout);
    }

    @Test
    void standardStreamTimeoutRemainsFiveMinutes() {
        Long timeout = (Long) ReflectionTestUtils.getField(AgentChatController.class, "STREAM_TIMEOUT_MS");

        assertEquals(300000L, timeout);
    }

    @Test
    void disabledDeepAgentDoesNotDispatchRun() {
        AgentChatService chatService = mock(AgentChatService.class);
        AgentDefinition agent = deepAgent("agent-1");
        when(chatService.getEnabledAgent("agent-1"))
                .thenThrow(new ServerException(422, "disabled"));
        DeepAgentRunService deepRuns = mock(DeepAgentRunService.class);
        AgentChatController controller = controller(chatService, mock(AgentConversationService.class),
                mock(AgentDefinitionService.class), deepRuns, config(600L));
        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId(agent.getId());
        dto.setMessage("task");

        ServerException error = assertThrows(ServerException.class,
                () -> controller.stream(dto, new org.springframework.mock.web.MockHttpServletResponse()));

        assertEquals(true, error.getMessage().startsWith("422:"));
        verify(deepRuns, never()).startRun(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deepConversationForAnotherAgentUsesStandardMismatchErrorWithoutDispatch() {
        AgentChatService chatService = mock(AgentChatService.class);
        AgentDefinition agent = deepAgent("agent-1");
        when(chatService.getEnabledAgent("agent-1")).thenReturn(agent);
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        conversation.setUserId("user-1");
        conversation.setAgentDefinitionId("agent-2");
        conversation.setDeleted(false);
        AgentConversationService conversations = mock(AgentConversationService.class);
        when(conversations.getById("conversation-1")).thenReturn(conversation);
        DeepAgentRunService deepRuns = mock(DeepAgentRunService.class);
        AgentChatController controller = controller(chatService, conversations, mock(AgentDefinitionService.class),
                deepRuns, config(600L));
        HashMap<String, String> user = new HashMap<>();
        user.put("userId", "user-1");
        com.aether.local.CurrentUser.set(user);
        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId("agent-1");
        dto.setConversationId("conversation-1");
        dto.setMessage("task");

        ServerException error = assertThrows(ServerException.class,
                () -> controller.stream(dto, new org.springframework.mock.web.MockHttpServletResponse()));

        assertEquals(true, error.getMessage().startsWith("422:"));
        verify(deepRuns, never()).startRun(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
    }

    private AgentChatController controller(AgentDefinitionService definitions, DeepAgentConfig config) {
        return controller(mock(AgentChatService.class), mock(AgentConversationService.class), definitions,
                mock(DeepAgentRunService.class), config);
    }

    private AgentChatController controller(AgentChatService chatService, AgentConversationService conversations,
                                           AgentDefinitionService definitions, DeepAgentRunService deepRuns,
                                           DeepAgentConfig config) {
        return new AgentChatController(chatService, conversations,
                mock(AgentMessageService.class), mock(ChatAttachmentService.class), definitions,
                deepRuns, mock(DeepAgentCallbackController.class),
                mock(KnowledgeContextService.class), config);
    }

    private AgentDefinition deepAgent(String id) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(id);
        agent.setExecutionMode("DEEP");
        return agent;
    }

    private DeepAgentConfig config(long runTimeoutSeconds) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setRunTimeoutSeconds(runTimeoutSeconds);
        return config;
    }
}
