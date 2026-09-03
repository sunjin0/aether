package com.aether.agent.observability;

/**
 * Compatibility shim for legacy call sites. Observability has been removed;
 * timing data is intentionally discarded.
 */
public final class ChatLatencyMetrics {
    private ChatLatencyMetrics() {
    }

    public static void record(String name, long value) {
        // No-op: metrics collection is no longer part of the application.
    }
}
