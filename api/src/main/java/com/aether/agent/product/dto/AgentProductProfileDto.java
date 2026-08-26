package com.aether.agent.product.dto;
import lombok.Data;
@Data public class AgentProductProfileDto {
    private String applicationId, agentDefinitionId, productType, name, inputSchema, outputSchema, knowledgePolicy, approvalPolicy, handoffPolicy;
}
