package com.aether.workflow.service.impl;

import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.sys.service.ServiceAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

class AgentWorkflowWebhookTriggerServiceImplTest {

    @Test
    void failureSummaryRedactsCredentialsAndBasicAuthUrl() {
        AgentWorkflowWebhookTriggerServiceImpl service = new AgentWorkflowWebhookTriggerServiceImpl(
                mock(AgentWorkflowService.class), mock(AgentWorkflowExecutionService.class),
                mock(ServiceAccountService.class), 300000L);

        String summary = ReflectionTestUtils.invokeMethod(service, "safeFailureMessage",
                new RuntimeException("POST https://alice:super-secret@example.test failed token=abc123"));

        assertFalse(summary.contains("super-secret"));
        assertFalse(summary.contains("abc123"));
        assertTrue(summary.contains("[REDACTED]"));
    }

    @Test
    void alertmanagerPayloadRequiresFiringOrResolvedAlerts() {
        AgentWorkflowWebhookTriggerServiceImpl service = new AgentWorkflowWebhookTriggerServiceImpl(
                mock(AgentWorkflowService.class), mock(AgentWorkflowExecutionService.class),
                mock(ServiceAccountService.class), 300000L);
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("status", "firing");
        invalid.put("alerts", Collections.emptyList());
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "validateAlertmanagerPayload", "ai-sre-alert", invalid));

        Map<String, Object> missingIdentity = new LinkedHashMap<>();
        missingIdentity.put("status", "firing");
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("status", "firing");
        alert.put("labels", Collections.singletonMap("severity", "critical"));
        missingIdentity.put("alerts", Collections.singletonList(alert));
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "validateAlertmanagerPayload", "ai-sre-alert", missingIdentity));

        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("status", "firing");
        Map<String, Object> validAlert = new LinkedHashMap<>();
        validAlert.put("status", "firing");
        validAlert.put("labels", Collections.singletonMap("alertname", "HighErrorRate"));
        valid.put("alerts", Collections.singletonList(validAlert));
        ReflectionTestUtils.invokeMethod(service, "validateAlertmanagerPayload", "ai-sre-alert", valid);
    }

    @Test
    void alertmanagerPayloadHasBoundedBatchSize() {
        AgentWorkflowWebhookTriggerServiceImpl service = new AgentWorkflowWebhookTriggerServiceImpl(
                mock(AgentWorkflowService.class), mock(AgentWorkflowExecutionService.class),
                mock(ServiceAccountService.class), 300000L);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "firing");
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("status", "firing");
        alert.put("labels", Collections.singletonMap("alertname", "HighErrorRate"));
        payload.put("alerts", new ArrayList<>(Collections.nCopies(101, alert)));

        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "validateAlertmanagerPayload", "ai-sre-alert", payload));
    }
}
