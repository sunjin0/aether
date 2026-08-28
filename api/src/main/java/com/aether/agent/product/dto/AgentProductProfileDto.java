package com.aether.agent.product.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("智能体产品档案创建或更新请求")
public class AgentProductProfileDto {
    @ApiModelProperty(value = "所属应用 ID", required = true, example = "app-support")
    private String applicationId;
    @ApiModelProperty(value = "唯一产品编码", required = true, example = "support-chat")
    private String code;
    @ApiModelProperty(value = "已发布智能体定义 ID", example = "agent-123")
    private String agentDefinitionId;
    @ApiModelProperty(value = "已发布工作流 ID", example = "workflow-123")
    private String workflowId;
    @ApiModelProperty(value = "产品类型", required = true, example = "AGENT")
    private String productType;
    @ApiModelProperty(value = "产品名称", required = true, example = "Customer Support Assistant")
    private String name;
    @ApiModelProperty(value = "输入 JSON 架构", example = "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"}}}")
    private String inputSchema;
    @ApiModelProperty(value = "输出 JSON 架构", example = "{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}}}")
    private String outputSchema;
    @ApiModelProperty(value = "知识库访问策略", example = "AUTO")
    private String knowledgePolicy;
    @ApiModelProperty(value = "工具审批策略", example = "risky")
    private String approvalPolicy;
    @ApiModelProperty(value = "人工转接策略", example = "WHEN_UNRESOLVED")
    private String handoffPolicy;
    @ApiModelProperty(value = "OpenAPI 协议版本", example = "v1")
    private String apiProtocolVersion;
    @ApiModelProperty(value = "接受 OpenAPI 调用方传入的逗号分隔上下文键", example = "customerTier,locale")
    private String allowedContextKeys;
}
