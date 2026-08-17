package com.aether.agent.executor;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具执行器工厂。
 */
@Component
public class ToolExecutorFactory {

    private final List<ToolExecutor> executors;

    /**
     * 创建 {@code ToolExecutorFactory} 实例。
     */
    public ToolExecutorFactory(List<ToolExecutor> executors) {
        this.executors = executors;
    }

    /**
     * 根据工具类型获取执行器
     */
    public ToolExecutor getExecutor(String toolType) {
        for (ToolExecutor executor : executors) {
            if (executor.supports(toolType)) {
                return executor;
            }
        }
        throw new IllegalArgumentException("不支持的工具类型: " + toolType);
    }
}
