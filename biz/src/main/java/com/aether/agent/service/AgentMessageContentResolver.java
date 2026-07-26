package com.aether.agent.service;

import com.aether.agent.entity.AgentMessage;
import org.apache.commons.lang3.StringUtils;

/** Resolves the content that is safe and appropriate to send to an AI model. */
public final class AgentMessageContentResolver {

    private AgentMessageContentResolver() {
    }

    /**
     * User messages prefer their persisted context-complete form. Existing
     * messages and rewrite failures intentionally fall back to the original
     * content so that a missing rewrite never blocks a conversation.
     */
    public static String getEffectiveContent(AgentMessage message) {
        if (message == null) {
            return "";
        }
        if ("user".equals(message.getRole())) {
            return StringUtils.defaultIfBlank(message.getRewrittenContent(), message.getContent());
        }
        return StringUtils.defaultString(message.getContent());
    }
}
