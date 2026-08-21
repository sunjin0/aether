package com.aether.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 外部系统提交 Agent 运行请求。
 */
@Data
public class BusinessAgentRunCreateDto {
    private String message;
    private String conversationId;
    private String idempotencyKey;
    private Map<String, Object> variables;
    private Map<String, Object> metadata;
}
