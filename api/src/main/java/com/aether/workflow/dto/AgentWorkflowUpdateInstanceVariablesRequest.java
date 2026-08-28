package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AgentWorkflowUpdateInstanceVariablesRequest {
    @ApiModelProperty(value = "开始节点变量", required = false, example = "{\"orderId\":\"order-001\"}") private Map<String, Object> variables;
}
