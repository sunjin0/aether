package com.aether.workflow.runtime;

import com.aether.workflow.config.WorkflowCallbackProperties;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WorkflowCallbackServiceTest {

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

    @Test
    void rejectsCallbackWhenSigningSecretIsMissing() {
        WorkflowCallbackProperties properties = new WorkflowCallbackProperties();
        properties.setEnabled(true);
        properties.setAllowedHosts(Arrays.asList("workflow.example.com"));
        WorkflowCallbackService service = new WorkflowCallbackService(mock(AgentWorkflowCallbackDeliveryService.class), properties, Runnable::run, mock(AgentWorkflowVersionService.class), new WorkflowSensitiveDataSanitizer("secret,token"));

        assertThrows(IllegalArgumentException.class, () -> service.validateCallbackUrl("https://workflow.example.com/events"));
    }
}
