package com.aether.openapi.dto;

import lombok.Data;
import java.util.Map;

/** 外部业务系统的同步 Agent 问答请求。 */
@Data
public class OpenApiAgentChatDto {
    private String agentCode;
    private String conversationId;
    private String businessId;
    private String idempotencyKey;
    private String input;
    private Map<String, Object> context;
}
