package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AgentWorkflowCreateWebhookRequest {
    @ApiModelProperty(value = "工作流 ID", required = true, example = "workflow-001") private String workflowId;
    @ApiModelProperty(value = "服务账号 ID", required = true, example = "service-account-001") private String serviceAccountId;
    @ApiModelProperty(value = "Webhook 名称", required = true, example = "订单创建事件") private String name;
    @ApiModelProperty(value = "业务类型", required = true, example = "ORDER") private String businessType;
    @ApiModelProperty(value = "从请求体提取业务 ID 的表达式", required = true, example = "$.orderId") private String businessIdExpression;
    @ApiModelProperty(value = "从请求体提取幂等键的表达式", required = true, example = "$.eventId") private String idempotencyKeyExpression;
    @ApiModelProperty(value = "工作流变量到请求体表达式的映射", required = false, example = "{\"orderId\":\"$.orderId\"}") private Map<String, String> variableMapping;
}
