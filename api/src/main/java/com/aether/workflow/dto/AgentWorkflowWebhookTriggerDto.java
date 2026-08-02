package com.aether.workflow.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AgentWorkflowWebhookTriggerDto {
    private String workflowId;
    private String serviceAccountId;
    private String name;
    private String businessType;
    private String businessIdExpression;
    private String idempotencyKeyExpression;
    private Map<String, String> variableMapping;
}
