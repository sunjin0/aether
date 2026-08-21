package com.aether.agent.service;

import com.aether.agent.entity.AgentSession;
import com.aether.sys.service.AdminPreferenceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 统一清理由会话记忆派生出来的上下文、摘要和偏好缓存。
 */
@Service
public class AgentDerivedContextInvalidationService {
    private final AgentSessionService sessionService;
    private final ConversationCacheService conversationCacheService;
    private final ConversationSummaryService conversationSummaryService;
    private final AdminPreferenceService adminPreferenceService;

    /**
     * 创建 {@code AgentDerivedContextInvalidationService} 实例。
     */
    public AgentDerivedContextInvalidationService(AgentSessionService sessionService,
                                                  ConversationCacheService conversationCacheService,
                                                  ConversationSummaryService conversationSummaryService,
                                                  AdminPreferenceService adminPreferenceService) {
        this.sessionService = sessionService;
        this.conversationCacheService = conversationCacheService;
        this.conversationSummaryService = conversationSummaryService;
        this.adminPreferenceService = adminPreferenceService;
    }

    /**
     * 根据 Session 清理派生上下文。
     */
    public void invalidateSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        AgentSession session = sessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        invalidateConversation(session.getConversationId(), session.getUserId());
    }

    /**
     * 根据会话清理派生上下文。
     */
    public void invalidateConversation(String conversationId, String userId) {
        if (StringUtils.isBlank(conversationId) && StringUtils.isBlank(userId)) {
            return;
        }
        Runnable cleanup = () -> {
            if (StringUtils.isNotBlank(conversationId)) {
                conversationCacheService.evict(conversationId);
                conversationSummaryService.evict(conversationId);
            }
            if (StringUtils.isNotBlank(userId)) {
                adminPreferenceService.clearUserCache(userId);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 处理afterCommit。
                 */
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }
}
