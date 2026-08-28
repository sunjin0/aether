package com.aether.agent.executor;

import com.aether.agent.entity.AgentTool;
import lombok.Data;

import java.util.Map;

/**
 * 工具执行上下文。
 */
@Data
public class ToolExecutionContext {

    /**
     * 工具配置
     */
    private AgentTool tool;

    /**
     * 工具调用参数（模型传入）
     */
    private Map<String, Object> arguments;

    /**
     * 运行记录ID
     */
    private String runId;

    /**
     * 调用者ID
     */
    private String userId;

    /**
     * Agent 定义 ID，用于签发 MCP 工具的最小权限委派令牌。
     */
    private String agentDefinitionId;

    /**
     * 工作流写操作的稳定幂等键，会以 X-Aether-Idempotency-Key 透传到 MCP 服务。
     */
    private String idempotencyKey;

    /** OpenAPI product/application boundary resolved from the durable run, not model arguments. */
    private String applicationId;
    private String productProfileId;
    private String serviceAccountId;
    /** Encrypted trusted context; executors must not merge it into model-provided arguments. */
    private String trustedContext;
}
