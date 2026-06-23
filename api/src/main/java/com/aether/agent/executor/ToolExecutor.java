package com.aether.agent.executor;

/**
 * 工具执行器接口。
 */
public interface ToolExecutor {

    /**
     * 是否支持该工具类型
     */
    boolean supports(String toolType);

    /**
     * 执行工具调用
     */
    ToolExecutionResult execute(ToolExecutionContext context);
}
