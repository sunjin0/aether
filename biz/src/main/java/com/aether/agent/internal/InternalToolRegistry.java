package com.aether.agent.internal;

import com.aether.agent.entity.AgentTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合全部平台注册的内建工具。
 */
@Component
public class InternalToolRegistry {

    private final List<InternalToolHandler> handlers;

    public InternalToolRegistry(List<InternalToolHandler> handlers) {
        this.handlers = handlers;
    }

    public List<AgentTool> getTools() {
        List<AgentTool> tools = new ArrayList<>();
        for (InternalToolHandler handler : handlers) {
            tools.add(handler.getTool());
        }
        return tools;
    }

    public InternalToolHandler getHandler(String toolName) {
        for (InternalToolHandler handler : handlers) {
            if (handler.supports(toolName)) {
                return handler;
            }
        }
        return null;
    }
}
