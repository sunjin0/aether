package com.aether.openapi.vo;

import lombok.Data;

/** 外部业务系统可见的异步 Agent 运行状态，不包含内部错误与工具详情。 */
@Data
public class OpenApiAgentRunVo {
    private String runId;
    private String conversationId;
    private String businessId;
    private String status;
    private String answer;
    private String errorCode;
    private String traceId;
}
