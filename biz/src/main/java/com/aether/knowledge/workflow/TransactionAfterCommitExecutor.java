package com.aether.knowledge.workflow;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 表示TransactionAfterCommitExecutor。
 */
@Component
public class TransactionAfterCommitExecutor {

    /**
     * 执行当前请求。
     */
    public void execute(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    /**
                     * 处理afterCommit。
                     */
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
