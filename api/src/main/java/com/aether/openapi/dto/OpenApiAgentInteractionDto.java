package com.aether.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/** A structured response to a pending OpenAPI Agent interaction. */
@Data
@ApiModel("OpenAPI 待处理交互回答请求")
public class OpenApiAgentInteractionDto {
    @ApiModelProperty(value = "用于安全重试回答提交的稳定唯一键", required = true, example = "interaction-approval-001")
    private String idempotencyKey;
    @ApiModelProperty(value = "待处理交互请求的结构化回答", required = true, example = "{\"approved\":true}")
    private Map<String, Object> answer;
}
