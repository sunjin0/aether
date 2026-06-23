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
}
