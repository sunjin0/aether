package com.aether.agent.internal;

import com.aether.agent.entity.AgentTool;

import java.util.Map;

/**
 * 平台内建工具处理器。内建工具不需要 Agent 配置或绑定。
 */
public interface InternalToolHandler {

    AgentTool getTool();

    boolean supports(String toolName);

    InternalToolHandleResult handle(String conversationId, Map<String, Object> arguments);
}
