package com.aether.agent.controller;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.service.ModelProviderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 验证Deep智能体回调控制器的行为。
 */
@ExtendWith(MockitoExtension.class)
class DeepAgentCallbackControllerTest {

    @Mock
    private DeepAgentRunService deepAgentRunService;

    @Mock
    private DeepAgentConfig config;

    @Mock
    private AgentMessageService agentMessageService;

    @Mock
    private AgentStreamCallback streamCallback;

    @Mock
    private AgentRunPlanService planService;

    @Mock
    private AgentDefinitionService agentDefinitionService;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private ModelCatalogService modelCatalogService;

    /**
     * 处理completed回调SendsStructuredDoneResponseAndRemoves回调。
     */
    @Test
    void completedCallbackSendsStructuredDoneResponseAndRemovesCallback() {
        when(deepAgentRunService.completeRun("run-1", "final answer", "deep-model", 12, 8, 20,
                null, null, "[{\"name\":\"search\"}]", "[{\"title\":\"Reference\",\"url\":\"https://example.test\"}]",
                1500, 2000L)).thenReturn(new DeepAgentRunService.CompletedRun("conversation-1", "message-1", 1000L));
        when(streamCallback.isClosed()).thenReturn(false);
        DeepAgentCallbackController controller = controller();
        controller.registerCallback("run-1", streamCallback);

        ReflectionTestUtils.invokeMethod(controller, "handleCompleted", "run-1",
                "{\"conversation_id\":\"conversation-1\",\"message_id\":\"message-1\","
                        + "\"content\":\"final answer\",\"model\":\"deep-model\","
                        + "\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20,"
                        + "\"latency_ms\":1500,"
                        + "\"tool_calls\":[{\"name\":\"search\"}],"
                        + "\"sources\":[{\"title\":\"Reference\",\"url\":\"https://example.test\"}]}",
                streamCallback, 2000L);

        verify(deepAgentRunService).completeRun("run-1", "final answer", "deep-model", 12, 8, 20,
                null, null, "[{\"name\":\"search\"}]", "[{\"title\":\"Reference\",\"url\":\"https://example.test\"}]",
                1500, 2000L);
        ArgumentCaptor<ModelStreamResponse> responseCaptor = ArgumentCaptor.forClass(ModelStreamResponse.class);
        verify(streamCallback).onDone(eq("conversation-1"), eq("message-1"), responseCaptor.capture());
        ModelStreamResponse response = responseCaptor.getValue();
        assertEquals("final answer", response.getContent());
        assertEquals("deep-model", response.getModel());
        assertEquals(12, response.getPromptTokens());
        assertEquals(8, response.getCompletionTokens());
        assertEquals(20, response.getTotalTokens());
        assertEquals("[{\"name\":\"search\"}]", response.getToolCalls());
        assertEquals("Reference", response.getSources().get(0).get("title"));
        assertNull(response.getRawResponse());

        @SuppressWarnings("unchecked")
        Map<String, AgentStreamCallback> activeCallbacks =
                (Map<String, AgentStreamCallback>) ReflectionTestUtils.getField(controller, "activeCallbacks");
        assertFalse(activeCallbacks.containsKey("run-1"));
    }

    /**
     * 处理completed回调SendsPersisted运行会话And消息Ids。
     */
    @Test
    void completedCallbackSendsPersistedRunConversationAndMessageIds() {
        when(deepAgentRunService.completeRun("run-1", "final answer", "deep-model", 12, 8, 20,
                null, null, null, null, null, 2000L)).thenReturn(new DeepAgentRunService.CompletedRun("conversation-actual", "message-actual"));
        when(streamCallback.isClosed()).thenReturn(false);
        DeepAgentCallbackController controller = controller();
        controller.registerCallback("run-1", streamCallback);

        ReflectionTestUtils.invokeMethod(controller, "handleCompleted", "run-1",
                "{\"conversation_id\":\"untrusted\",\"message_id\":\"untrusted\",\"content\":\"final answer\","
                        + "\"model\":\"deep-model\",\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}",
                streamCallback, 2000L);

        verify(streamCallback).onDone(eq("conversation-actual"), eq("message-actual"),
                org.mockito.ArgumentMatchers.any(ModelStreamResponse.class));
    }

    /**
     * 处理staleCompleted回调DoesNot发送DoneOr移除Active回调。
     */
    @Test
    void staleCompletedCallbackDoesNotSendDoneOrRemoveActiveCallback() {
        when(deepAgentRunService.completeRun("run-1", "late answer", "deep-model", null, null, null,
                null, null, null, null, null, 0L)).thenReturn(null);
        DeepAgentCallbackController controller = controller();
        controller.registerCallback("run-1", streamCallback);

        ReflectionTestUtils.invokeMethod(controller, "handleCompleted", "run-1",
                "{\"content\":\"late answer\",\"model\":\"deep-model\"}", streamCallback, 0L);

        verify(streamCallback, never()).onDone(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(ModelStreamResponse.class));
        @SuppressWarnings("unchecked")
        Map<String, AgentStreamCallback> activeCallbacks =
                (Map<String, AgentStreamCallback>) ReflectionTestUtils.getField(controller, "activeCallbacks");
        assertEquals(streamCallback, activeCallbacks.get("run-1"));
    }

    /**
     * 处理staleFailed回调DoesNot发送ErrorOr移除Active回调。
     */
    @Test
    void staleFailedCallbackDoesNotSendErrorOrRemoveActiveCallback() throws Exception {
        when(config.getKeyId()).thenReturn("key-1");
        when(config.getSharedSecret()).thenReturn("test-secret");
        when(deepAgentRunService.handleCallback("run-1", "event-1", "run.failed", 1000L,
                "{\"error\":\"late failure\"}")).thenReturn(true);
        when(deepAgentRunService.markFailed("run-1", "late failure")).thenReturn(false);
        DeepAgentCallbackController controller = controller();
        controller.registerCallback("run-1", streamCallback);

        String body = "{\"run_id\":\"run-1\",\"event_id\":\"event-1\",\"event_type\":\"run.failed\","
                + "\"occurred_at\":1000,\"data\":{\"error\":\"late failure\"}}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Aether-Key-Id", "key-1");
        request.addHeader("X-Aether-Timestamp", timestamp);
        request.addHeader("X-Aether-Signature", signature("test-secret", timestamp + "." + body));

        ResponseEntity<Void> response = controller.callback("run-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(streamCallback, never()).onError(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
        @SuppressWarnings("unchecked")
        Map<String, AgentStreamCallback> activeCallbacks =
                (Map<String, AgentStreamCallback>) ReflectionTestUtils.getField(controller, "activeCallbacks");
        assertEquals(streamCallback, activeCallbacks.get("run-1"));
    }

    /**
     * 处理planApprovalRequiredCreatesPlanApprovalInteraction。
     */
    @Test
    void planApprovalRequiredCreatesPlanApprovalInteraction() throws Exception {
        when(config.getKeyId()).thenReturn("key-1");
        when(config.getSharedSecret()).thenReturn("test-secret");
        when(deepAgentRunService.handleCallback(eq("run-1"), eq("event-1"), eq("plan.approval.required"),
                eq(1000L), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        AgentMessage planMsg = new AgentMessage();
        planMsg.setId("msg-plan");
        planMsg.setConversationId("conversation-1");
        planMsg.setMessageType("interaction");
        planMsg.setInteractionStatus("pending");
        when(deepAgentRunService.createPlanApproval(eq("run-1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(planMsg);
        DeepAgentCallbackController controller = controller();
        controller.registerCallback("run-1", streamCallback);
        when(streamCallback.isClosed()).thenReturn(false);

        String body = "{\"run_id\":\"run-1\",\"event_id\":\"event-1\",\"event_type\":\"plan.approval.required\","
                + "\"occurred_at\":1000,\"data\":{\"plan\":[{\"title\":\"步骤一\"}]}}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Aether-Key-Id", "key-1");
        request.addHeader("X-Aether-Timestamp", timestamp);
        request.addHeader("X-Aether-Signature", signature("test-secret", timestamp + "." + body));

        ResponseEntity<Void> response = controller.callback("run-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(deepAgentRunService).createPlanApproval(eq("run-1"), org.mockito.ArgumentMatchers.anyString());
        verify(streamCallback).onQuestion(eq("conversation-1"), eq("run-1"), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 处理stepStarted回调MarksStepRunning。
     */
    @Test
    void stepStartedCallbackMarksStepRunning() throws Exception {
        when(config.getKeyId()).thenReturn("key-1");
        when(config.getSharedSecret()).thenReturn("test-secret");
        when(deepAgentRunService.handleCallback(eq("run-1"), eq("event-1"), eq("step.started"),
                eq(1000L), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        DeepAgentCallbackController controller = controller();

        String body = "{\"run_id\":\"run-1\",\"event_id\":\"event-1\",\"event_type\":\"step.started\","
                + "\"occurred_at\":1000,\"data\":{\"stepIndex\":3}}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Aether-Key-Id", "key-1");
        request.addHeader("X-Aether-Timestamp", timestamp);
        request.addHeader("X-Aether-Signature", signature("test-secret", timestamp + "." + body));

        ResponseEntity<Void> response = controller.callback("run-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(planService).markStepRunning("run-1", 3);
    }

    /**
     * 处理malformedSignature判断是否为RejectedBefore回调Processing。
     */
    @Test
    void malformedSignatureIsRejectedBeforeCallbackProcessing() {
        when(config.getKeyId()).thenReturn("key-1");
        DeepAgentCallbackController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Aether-Key-Id", "key-1");
        request.addHeader("X-Aether-Timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        request.addHeader("X-Aether-Signature", "not-hex");

        ResponseEntity<Void> response = controller.callback("run-1", request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(deepAgentRunService, never()).handleCallback(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 处理signature。
     */
    private String signature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    /**
     * 控制器当前请求。
     */
    private DeepAgentCallbackController controller() {
        return new DeepAgentCallbackController(deepAgentRunService, agentMessageService, config, planService,
                agentDefinitionService, modelProviderService, modelCatalogService);
    }
}
