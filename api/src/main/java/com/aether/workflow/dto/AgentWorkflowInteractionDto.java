package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 人工节点或工具确认节点的表单答复。
 */
@Data
@ApiModel("工作流交互回答请求")
public class AgentWorkflowInteractionDto {
    @ApiModelProperty(value = "待处理交互请求的回答字段", required = true, example = "{\"approved\":true,\"comment\":\"Approved by operations\"}")
    private Map<String, Object> answer;
}
