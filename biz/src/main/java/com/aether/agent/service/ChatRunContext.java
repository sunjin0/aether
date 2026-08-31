package com.aether.agent.service;

import java.util.concurrent.atomic.AtomicBoolean;

/** 所有聊天执行模式共享的请求关联与取消上下文。 */
public final class ChatRunContext implements com.aether.agent.model.CancellationToken {
    private final String requestId;
    private final String conversationId;
    private final String userId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public ChatRunContext(String requestId, String conversationId, String userId) {
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.userId = userId;
    }
    public String getRequestId() { return requestId; }
    public String getConversationId() { return conversationId; }
    public String getUserId() { return userId; }
    /** 判断本次运行是否已取消。 */
    public boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
    /** 标记本次运行已取消。 */
    public void cancel() { cancelled.set(true); }
    /** 已取消时立即中断当前执行。 */
    public void checkCancelled() {
        if (isCancelled()) throw new java.util.concurrent.CancellationException("Agent 运行已取消");
    }
}
