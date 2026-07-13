/*
 * Copyright (c) 2026. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package com.aether.agent.tools.entity;

import com.aether.agent.entity.AgentMessage;
import lombok.Data;

/**
 * 内建工具处理结果。
 */
@Data
public class ToolResult {

    private boolean waitingUser;
    private AgentMessage message;
    private String contextContent;

    public static ToolResult waitingUser(AgentMessage message, String contextContent) {
        ToolResult result = new ToolResult();
        result.setWaitingUser(true);
        result.setMessage(message);
        result.setContextContent(contextContent);
        return result;
    }
}
