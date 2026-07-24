package com.aether.knowledge.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionAfterCommitExecutorTest {
    private final TransactionAfterCommitExecutor executor =
            new TransactionAfterCommitExecutor();

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void executesImmediatelyWithoutTransactionSynchronization() {
        AtomicInteger calls = new AtomicInteger();

        executor.execute(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    void waitsUntilCommitWhenSynchronizationIsActive() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        executor.execute(calls::incrementAndGet);
        assertEquals(0, calls.get());

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotRunAfterRollbackCompletion() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        executor.execute(calls::incrementAndGet);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertEquals(0, calls.get());
    }
}
