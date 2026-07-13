/*
 * Copyright (c) 2026. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package com.aether.agent.tools.core;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.tools.entity.ToolResult;

import java.util.Map;

/**
 * 平台内建工具处理器。内建工具不需要 Agent 配置或绑定。
 */
public interface Tool {

    AgentTool getTool();

    boolean supports(String toolName);

    ToolResult handle(String conversationId, Map<String, Object> arguments);
}
