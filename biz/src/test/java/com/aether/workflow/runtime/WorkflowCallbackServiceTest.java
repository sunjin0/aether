package com.aether.workflow.runtime;

import com.aether.workflow.config.WorkflowCallbackProperties;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;

import java.lang.reflect.Method;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 验证工作流回调服务的行为。
 */
class WorkflowCallbackServiceTest {

    /**
     * 处理acceptsOnlyEnabledAllowlistedHttpCallbacksWithSigningSecret。
     */
    @Test
    void acceptsOnlyEnabledAllowlistedHttpCallbacksWithSigningSecret() {
        WorkflowCallbackProperties properties = new WorkflowCallbackProperties();
        properties.setEnabled(true);
        properties.setAllowedHosts(Arrays.asList("workflow.example.com"));
        properties.setSigningSecret("test-secret");
        WorkflowCallbackService service = new WorkflowCallbackService(mock(AgentWorkflowCallbackDeliveryService.class), properties, Runnable::run, mock(AgentWorkflowVersionService.class), new WorkflowSensitiveDataSanitizer("secret,token"));

        assertDoesNotThrow(() -> service.validateCallbackUrl("https://workflow.example.com/events"));
        assertThrows(IllegalArgumentException.class, () -> service.validateCallbackUrl("https://127.0.0.1/events"));
        assertThrows(IllegalArgumentException.class, () -> service.validateCallbackUrl("file:///tmp/events"));
    }

    /**
     * 处理rejects回调WhenSigningSecret判断是否为Missing。
     */
    @Test
    void rejectsCallbackWhenSigningSecretIsMissing() {
        WorkflowCallbackProperties properties = new WorkflowCallbackProperties();
        properties.setEnabled(true);
        properties.setAllowedHosts(Arrays.asList("workflow.example.com"));
        WorkflowCallbackService service = new WorkflowCallbackService(mock(AgentWorkflowCallbackDeliveryService.class), properties, Runnable::run, mock(AgentWorkflowVersionService.class), new WorkflowSensitiveDataSanitizer("secret,token"));

        assertThrows(IllegalArgumentException.class, () -> service.validateCallbackUrl("https://workflow.example.com/events"));
    }

    @Test
    void reusesPersistedTraceparentForAsyncDelivery() throws Exception {
        WorkflowCallbackProperties properties = new WorkflowCallbackProperties();
        properties.setEnabled(true);
        properties.setAllowedHosts(Arrays.asList("workflow.example.com"));
        properties.setSigningSecret("test-secret");
        WorkflowCallbackService service = new WorkflowCallbackService(mock(AgentWorkflowCallbackDeliveryService.class), properties,
                Runnable::run, mock(AgentWorkflowVersionService.class), new WorkflowSensitiveDataSanitizer("secret,token"));
        AgentWorkflowCallbackDelivery delivery = new AgentWorkflowCallbackDelivery();
        delivery.setId("delivery-1");
        delivery.setEventType("workflow.completed");
        delivery.setPayload("{}");
        delivery.setTraceparent("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        MDC.clear();
        Method request = WorkflowCallbackService.class.getDeclaredMethod("request", AgentWorkflowCallbackDelivery.class);
        request.setAccessible(true);
        HttpEntity<?> entity = (HttpEntity<?>) request.invoke(service, delivery);
        assertEquals(delivery.getTraceparent(), entity.getHeaders().getFirst("traceparent"));
    }
}
