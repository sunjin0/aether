package com.aether.agent.product.dto;
import lombok.Data;
@Data public class AgentProductProfileDto {
    private String applicationId, code, agentDefinitionId, workflowId, productType, name, inputSchema, outputSchema, knowledgePolicy, approvalPolicy, handoffPolicy;
}
