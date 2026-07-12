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
import com.aether.agent.model.ModelStreamCallback;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.executor.ToolExecutorFactory;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
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
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private AgentToolService agentToolService;
    @Mock
    private AgentToolBindingService agentToolBindingService;
    @Mock
    private AgentToolCallLogService agentToolCallLogService;
    @Mock
    private ToolExecutorFactory toolExecutorFactory;
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
                modelClientFactory,
                redisTemplate,
                agentToolService,
                agentToolBindingService,
                agentToolCallLogService,
                toolExecutorFactory);
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
        response.setReasoningContent("先理解用户问候，再给出简短回应");
        response.setModel("gpt-test");
        response.setPromptTokens(3);
        response.setCompletionTokens(4);
        response.setTotalTokens(7);
        response.setReasoningTokens(2);

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
        org.junit.jupiter.api.Assertions.assertNull(result.getReasoningContent());
        org.junit.jupiter.api.Assertions.assertNull(result.getReasoningTokens());

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

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        AgentMessage assistantMessage = messageCaptor.getAllValues().get(1);
        org.junit.jupiter.api.Assertions.assertNull(assistantMessage.getReasoningContent());
        org.junit.jupiter.api.Assertions.assertNull(assistantMessage.getReasoningTokens());
    }

    @Test
    void chatBlocksClaimedToolResultWithoutSuccessfulToolLog() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setStatus(1);
        agent.setModelProviderId("provider-1");
        agent.setModel("gpt-test");
        agent.setTemperature(new BigDecimal("0.70"));
        agent.setMaxTokens(128);

        ModelProvider provider = new ModelProvider();
        provider.setId("provider-1");
        provider.setType("openai");
        provider.setStatus(1);

        ModelChatResponse response = new ModelChatResponse();
        response.setContent("I called the tool and the result is 28C.");
        response.setModel("gpt-test");
        response.setPromptTokens(3);
        response.setCompletionTokens(8);
        response.setTotalTokens(11);

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
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);

        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId("agent-1");
        dto.setMessage("weather?");

        AgentMessageVo result = service.chat(dto);

        org.junit.jupiter.api.Assertions.assertTrue(result.getContent().contains("工具调用未获得可信结果"));

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        AgentMessage assistantMessage = messageCaptor.getAllValues().get(1);
        org.junit.jupiter.api.Assertions.assertTrue(assistantMessage.getContent().contains("工具调用未获得可信结果"));

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals(1, runCaptor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(runCaptor.getValue().getErrorMsg().contains("没有成功工具执行记录"));
    }

    @Test
    void streamCreatesConversationEmitsChunksAndPersistsAssistantMessageAndRun() {
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
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.stream(any(), any())).thenAnswer(invocation -> {
            ModelStreamCallback callback = invocation.getArgument(1);
            callback.onMessage("你");
            callback.onMessage("好");
            ModelStreamResponse response = new ModelStreamResponse();
            response.setContent("你好");
            response.setReasoningContent("先理解用户问候");
            response.setModel("gpt-test");
            response.setPromptTokens(3);
            response.setCompletionTokens(2);
            response.setTotalTokens(5);
            response.setReasoningTokens(1);
            return response;
        });

        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId("agent-1");
        dto.setMessage("你好");
        RecordingStreamCallback callback = new RecordingStreamCallback();

        service.stream(dto, callback);

        assertEquals(2, callback.chunks.size());
        assertEquals("conversation-1:你", callback.chunks.get(0));
        assertEquals("conversation-1:好", callback.chunks.get(1));
        assertEquals("conversation-1", callback.doneConversationId);
        assertEquals("message-assistant-1", callback.doneMessageId);
        assertEquals(5, callback.doneResponse.getTotalTokens());
        assertFalse(callback.errorCalled);

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertEquals("assistant", messageCaptor.getAllValues().get(1).getRole());
        assertEquals("你好", messageCaptor.getAllValues().get(1).getContent());
        org.junit.jupiter.api.Assertions.assertNull(messageCaptor.getAllValues().get(1).getReasoningContent());
        assertEquals(5, messageCaptor.getAllValues().get(1).getTotalTokens());
        org.junit.jupiter.api.Assertions.assertNull(messageCaptor.getAllValues().get(1).getReasoningTokens());

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals("message-assistant-1", runCaptor.getValue().getMessageId());
        assertEquals("你好", runCaptor.getValue().getOutputContent());
        assertEquals(0, runCaptor.getValue().getStatus());
    }

    @Test
    void streamRejectsEmptyProviderResponse() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setStatus(1);
        agent.setModelProviderId("provider-1");
        agent.setModel("google/gemma-4-e4b");
        agent.setTemperature(new BigDecimal("0.70"));
        agent.setMaxTokens(128);

        ModelProvider provider = new ModelProvider();
        provider.setId("provider-1");
        provider.setName("google");
        provider.setType("openai");
        provider.setStatus(1);

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
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.stream(any(), any())).thenReturn(emptyStreamResponse("google/gemma-4-e4b"));

        AgentChatDto dto = new AgentChatDto();
        dto.setAgentId("agent-1");
        dto.setMessage("你好");
        RecordingStreamCallback callback = new RecordingStreamCallback();

        service.stream(dto, callback);

        org.junit.jupiter.api.Assertions.assertTrue(callback.errorCalled);
        assertEquals(500, callback.errorCode);
        assertEquals("模型响应内容为空", callback.errorMessage);

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService, org.mockito.Mockito.times(1)).save(messageCaptor.capture());
        assertEquals("user", messageCaptor.getValue().getRole());
    }

    private static ModelStreamResponse emptyStreamResponse(String model) {
        ModelStreamResponse response = new ModelStreamResponse();
        response.setContent("");
        response.setReasoningContent("");
        response.setModel(model);
        response.setPromptTokens(2725);
        response.setCompletionTokens(1);
        response.setTotalTokens(2726);
        response.setReasoningTokens(0);
        response.setRawResponse("{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"\",\"tool_calls\":[]},\"finish_reason\":\"stop\"}]}");
        return response;
    }

    private static class RecordingStreamCallback implements AgentStreamCallback {

        private final List<String> chunks = new ArrayList<>();
        private boolean errorCalled;
        private int errorCode;
        private String errorMessage;
        private String doneConversationId;
        private String doneMessageId;
        private ModelStreamResponse doneResponse;

        @Override
        public void onMessage(String conversationId, String chunk) {
            chunks.add(conversationId + ":" + chunk);
        }

        @Override
        public void onReasoning(String conversationId, String chunk) {
        }

        @Override
        public void onToolCall(String conversationId, String toolCallJson) {
        }

        @Override
        public void onDone(String conversationId, String messageId, ModelStreamResponse response) {
            this.doneConversationId = conversationId;
            this.doneMessageId = messageId;
            this.doneResponse = response;
        }

        @Override
        public void onError(int code, String message) {
            this.errorCalled = true;
            this.errorCode = code;
            this.errorMessage = message;
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
