package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 表示智能体工作流WebhookTriggerDTO。
 */
@Data
@ApiModel("Webhook 工作流触发器创建请求")
public class AgentWorkflowWebhookTriggerDto {
    @ApiModelProperty(value = "工作流 ID", required = true, example = "workflow-123")
    private String workflowId;
    @ApiModelProperty(value = "用于启动工作流的服务账号 ID", required = true, example = "service-account-123")
    private String serviceAccountId;
    @ApiModelProperty(value = "Webhook 触发器名称", required = true, example = "Order created webhook")
    private String name;
    @ApiModelProperty(value = "业务事件类型", required = true, example = "order.created")
    private String businessType;
    @ApiModelProperty(value = "从 Webhook 载荷提取业务 ID 的表达式", required = true, example = "$.data.orderId")
    private String businessIdExpression;
    @ApiModelProperty(value = "提取可安全重试幂等键的表达式", example = "$.data.eventId")
    private String idempotencyKeyExpression;
    @ApiModelProperty(value = "工作流变量名到 Webhook 载荷表达式的映射", example = "{\"orderId\":\"$.data.orderId\",\"amount\":\"$.data.total\"}")
    private Map<String, String> variableMapping;
}
