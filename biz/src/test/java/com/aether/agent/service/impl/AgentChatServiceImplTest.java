package com.aether.agent.service.impl;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.local.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceImplTest {

    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private ModelProviderService modelProviderService;
    @Mock
    private AgentConversationService agentConversationService;
    @Mock
    private AgentMessageService agentMessageService;
    @Mock
    private AgentRunService agentRunService;
    @Mock
    private ModelClientFactory modelClientFactory;
    @Mock
    private ModelClient modelClient;

    private AgentChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentChatServiceImpl(
                agentDefinitionService,
                modelProviderService,
                agentConversationService,
                agentMessageService,
                agentRunService,
                modelClientFactory);
        HashMap<String, String> user = new HashMap<>();
        user.put("userId", "user-1");
        CurrentUser.set(user);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

    @Test
    void chatCreatesConversationAndPersistsMessagesAndRun() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setStatus(1);
        agent.setModelProviderId("provider-1");
        agent.setModel("gpt-test");
        agent.setSystemPrompt("你是测试助手");
        agent.setTemperature(new BigDecimal("0.70"));
        agent.setMaxTokens(128);

        ModelProvider provider = new ModelProvider();
        provider.setId("provider-1");
        provider.setType("openai");
        provider.setStatus(1);

        ModelChatResponse response = new ModelChatResponse();
        response.setContent("你好，我是助手");
        response.setModel("gpt-test");
        response.setPromptTokens(3);
        response.setCompletionTokens(4);
        response.setTotalTokens(7);

        when(agentDefinitionService.getById("agent-1")).thenReturn(agent);
        when(modelProviderService.getById("provider-1")).thenReturn(provider);
        when(agentConversationService.save(any(AgentConversation.class))).thenAnswer(invocation -> {
            AgentConversation conversation = invocation.getArgument(0);
            conversation.setId("conversation-1");
            return true;
        });
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            if ("user".equals(message.getRole())) {
                message.setId("message-user-1");
            } else {
                message.setId("message-assistant-1");
            }
            return true;
        });
        when(agentMessageService.list(any())).thenReturn(new ArrayList<AgentMessage>());
        when(agentMessageService.count(any())).thenReturn(2L);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId("agent-1");
        dto.setMessage("你好");

        AgentMessageVo result = service.chat(dto);

        assertEquals("message-assistant-1", result.getId());
        assertEquals("conversation-1", result.getConversationId());
        assertEquals("assistant", result.getRole());
        assertEquals("你好，我是助手", result.getContent());

        ArgumentCaptor<AgentConversation> conversationCaptor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(agentConversationService).save(conversationCaptor.capture());
        assertEquals("user-1", conversationCaptor.getValue().getUserId());
        assertEquals("agent-1", conversationCaptor.getValue().getAgentDefinitionId());
        assertEquals(0, conversationCaptor.getValue().getStatus());

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals("agent-1", runCaptor.getValue().getAgentDefinitionId());
        assertEquals("provider-1", runCaptor.getValue().getModelProviderId());
        assertEquals("conversation-1", runCaptor.getValue().getConversationId());
        assertEquals("message-assistant-1", runCaptor.getValue().getMessageId());
        assertEquals(0, runCaptor.getValue().getStatus());
        assertEquals(7, runCaptor.getValue().getTotalTokens());
    }
}
