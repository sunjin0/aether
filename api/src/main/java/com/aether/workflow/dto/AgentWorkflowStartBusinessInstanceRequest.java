package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AgentWorkflowStartBusinessInstanceRequest {
    @ApiModelProperty(value = "开始节点变量", required = false, example = "{\"orderId\":\"order-001\"}") private Map<String, Object> variables;
    @ApiModelProperty(value = "业务类型", required = true, example = "ORDER") private String businessType;
    @ApiModelProperty(value = "业务标识", required = true, example = "order-001") private String businessId;
    @ApiModelProperty(value = "幂等键", required = true, example = "order-001-created") private String idempotencyKey;
    @ApiModelProperty(value = "终态回调地址", required = false, example = "https://example.com/workflow-callback") private String callbackUrl;
    @ApiModelProperty(value = "人工等待截止 Unix 毫秒", required = false, example = "1735689600000") private Long deadlineAt;
}
