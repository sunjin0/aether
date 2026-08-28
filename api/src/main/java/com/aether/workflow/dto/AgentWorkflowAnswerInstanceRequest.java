package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AgentWorkflowAnswerInstanceRequest {
    @ApiModelProperty(value = "人工节点回答或 MCP 确认数据", required = true, example = "{\"approved\":true}") private Map<String, Object> answer;
}
