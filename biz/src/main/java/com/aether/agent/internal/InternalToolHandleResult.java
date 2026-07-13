package com.aether.agent.internal;

import com.aether.agent.entity.AgentMessage;
import lombok.Data;

/**
 * 内建工具处理结果。
 */
@Data
public class InternalToolHandleResult {

    private boolean waitingUser;
    private AgentMessage message;
    private String contextContent;

    public static InternalToolHandleResult waitingUser(AgentMessage message, String contextContent) {
        InternalToolHandleResult result = new InternalToolHandleResult();
        result.setWaitingUser(true);
        result.setMessage(message);
        result.setContextContent(contextContent);
        return result;
    }
}
