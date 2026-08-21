package com.aether.agent.vo;

import lombok.Data;

/**
 * 外部系统可见的 Agent 运行状态。
 */
@Data
public class BusinessAgentRunVo {
    private String runId;
    private String agentId;
    private String conversationId;
    private String status;
    private String output;
    private String errorMessage;
    private Long createdAt;
    private Long updatedAt;
}
