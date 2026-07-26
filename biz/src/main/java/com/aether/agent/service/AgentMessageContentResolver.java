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

    /** Returns the complete model context, including a persisted file extraction when present. */
    public static String getContextContent(AgentMessage message) {
        String content = getEffectiveContent(message);
        if (!"user".equals(message.getRole()) || StringUtils.isBlank(message.getAttachmentContent())) {
            return content;
        }
        return content + "\n\n【用户上传文件的识别内容】\n"
                + message.getAttachmentContent()
                + "\n【文件识别内容结束】";
    }
}
