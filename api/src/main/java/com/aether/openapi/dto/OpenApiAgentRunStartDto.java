package com.aether.openapi.dto;

import lombok.Data;

import java.util.Map;

/** 外部业务系统提交的异步 Agent 运行请求。 */
@Data
public class OpenApiAgentRunStartDto {
    private String agentCode;
    private String conversationId;
    private String businessId;
    private String idempotencyKey;
    private String input;
    private Map<String, Object> context;
}
