/*
 * Copyright (c) 2026. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package com.aether.agent.tools.core;

import com.aether.agent.entity.AgentTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合全部平台注册的内建工具。
 */
@Component
public class ToolRegistry {

    private final List<Tool> handlers;

    /**
     * 创建 {@code ToolRegistry} 实例。
     */
    public ToolRegistry(List<Tool> handlers) {
        this.handlers = handlers;
    }

    /**
     * 获取Tools。
     */
    public List<AgentTool> getTools() {
        List<AgentTool> tools = new ArrayList<>();
        for (Tool handler : handlers) {
            tools.add(handler.getTool());
        }
        return tools;
    }

    /**
     * 获取Handler。
     */
    public Tool getHandler(String toolName) {
        for (Tool handler : handlers) {
            if (handler.supports(toolName)) {
                return handler;
            }
        }
        return null;
    }
}
