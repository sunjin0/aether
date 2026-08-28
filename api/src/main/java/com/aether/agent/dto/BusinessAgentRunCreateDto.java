package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 外部系统提交 Agent 运行请求。
 */
@Data
@ApiModel("业务智能体运行请求")
public class BusinessAgentRunCreateDto {
    @ApiModelProperty(value = "用户消息", required = true, example = "Draft a response to this support case.")
    private String message;
    @ApiModelProperty(value = "已有会话 ID；省略时创建会话", example = "conversation-123")
    private String conversationId;
    @ApiModelProperty(value = "用于安全重试提交的稳定唯一键", example = "case-CASE-2048-run-1")
    private String idempotencyKey;
    @ApiModelProperty(value = "提供给智能体的可选变量", example = "{\"caseId\":\"CASE-2048\"}")
    private Map<String, Object> variables;
    @ApiModelProperty(value = "可选调用方元数据", example = "{\"source\":\"crm\"}")
    private Map<String, Object> metadata;
}
