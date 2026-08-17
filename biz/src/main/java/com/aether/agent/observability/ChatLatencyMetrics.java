package com.aether.agent.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-process latency reservoir for chat-path diagnostics.
 * <p>
 * It deliberately has no request payload or identifier, and keeps only a bounded
 * window of durations. Deployments can scrape the structured percentile log until
 * a metrics backend is wired in.
 */
public final class ChatLatencyMetrics {
    private static final Logger log = LoggerFactory.getLogger(ChatLatencyMetrics.class);
    private static final int WINDOW_SIZE = 2048;
    private static final long REPORT_EVERY = 100L;
    private static final Map<String, Reservoir> RESERVOIRS = new ConcurrentHashMap<String, Reservoir>();

    /**
     * 创建 {@code ChatLatencyMetrics} 实例。
     */
    private ChatLatencyMetrics() {
    }

    /**
     * 处理record。
     */
    public static void record(String metric, long durationMs) {
        if (durationMs < 0) {
            return;
        }
        Reservoir reservoir = RESERVOIRS.computeIfAbsent(metric, key -> new Reservoir());
        Snapshot snapshot = reservoir.record(durationMs);
        if (snapshot != null) {
            log.info("聊天延迟分位数: metric={}, samples={}, p50={}ms, p95={}ms, p99={}ms",
                    metric, snapshot.samples, snapshot.p50, snapshot.p95, snapshot.p99);
        }
    }

    /**
     * 表示Reservoir。
     */
    private static final class Reservoir {
        private final long[] values = new long[WINDOW_SIZE];
        private int size;
        private int cursor;
        private long total;

        /**
         * 处理record。
         */
        private synchronized Snapshot record(long value) {
            values[cursor] = value;
            cursor = (cursor + 1) % WINDOW_SIZE;
            if (size < WINDOW_SIZE) {
                size++;
            }
            total++;
            if (total % REPORT_EVERY != 0 || size == 0) {
                return null;
            }
            long[] sample = Arrays.copyOf(values, size);
            Arrays.sort(sample);
            return new Snapshot(size, percentile(sample, 0.50D), percentile(sample, 0.95D), percentile(sample, 0.99D));
        }

        /**
         * 处理percentile。
         */
        private long percentile(long[] values, double quantile) {
            int index = (int) Math.ceil(quantile * values.length) - 1;
            return values[Math.max(0, Math.min(values.length - 1, index))];
        }
    }

    /**
     * 表示Snapshot。
     */
    private static final class Snapshot {
        private final int samples;
        private final long p50;
        private final long p95;
        private final long p99;

        /**
         * 创建 {@code Snapshot} 实例。
         */
        private Snapshot(int samples, long p50, long p95, long p99) {
            this.samples = samples;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }
    }
}
