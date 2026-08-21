package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * 验证上下文运营指标聚合。
 */
class AgentRunContextMetricServiceImplTest {

    /**
     * 聚合上下文压力、压缩状态和裁剪信号。
     */
    @Test
    void operationsMetricsAggregatesPressureCompressionAndTrimming() {
        AgentRunContextMetricServiceImpl service = spy(new AgentRunContextMetricServiceImpl());
        doReturn(Arrays.asList(
                metric("ANSWER", "FINAL", "NOT_NEEDED", 900, 1000, 2, 0),
                metric("DEEP_STEP", "FINAL", "NOT_NEEDED", 500, 1000, 0, 3),
                metric("COMPRESSION", "FINAL", "SYNC_COMPLETED", 120, 1000, 0, 4),
                metric("COMPRESSION", "FINAL", "FAILED_FALLBACK", 100, 1000, 0, 0),
                metric(null, "PRELIMINARY", null, 700, 1000, 1, 0)
        )).when(service).list(any());

        AgentContextOperationsMetricsVo result = service.operationsMetrics(123L);

        assertEquals(Long.valueOf(123L), result.getSinceCreatedAt());
        assertEquals(Long.valueOf(5L), result.getTotalMetricCount());
        assertEquals(Long.valueOf(2L), result.getCompletedRequestMetricCount());
        assertEquals(70D, result.getAverageOccupancyPercent());
        assertEquals(Long.valueOf(1L), result.getHighPressureMetricCount());
        assertEquals(Long.valueOf(2L), result.getCompressionMetricCount());
        assertEquals(Long.valueOf(1L), result.getCompressionCompletedCount());
        assertEquals(Long.valueOf(1L), result.getCompressionFailedFallbackCount());
        assertEquals(Long.valueOf(2L), result.getTrimmedMetricCount());
        assertEquals(Long.valueOf(3L), result.getTrimmedMessageCount());
        assertEquals(Long.valueOf(2L), result.getCompressedMetricCount());
        assertEquals(Long.valueOf(7L), result.getCompressedMessageCount());
        assertEquals(Long.valueOf(2L), result.getByCallType().get("ANSWER"));
        assertEquals(Long.valueOf(1L), result.getByCallType().get("DEEP_STEP"));
        assertEquals(Long.valueOf(2L), result.getByCallType().get("COMPRESSION"));
        assertEquals(Long.valueOf(1L), result.getByCompressionStatus().get("UNKNOWN"));
        assertFalse(result.getLatencyAvailable());
    }

    private AgentRunContextMetric metric(String callType, String phase, String compressionStatus,
                                         Integer promptTokens, Integer budget,
                                         Integer trimmed, Integer compressed) {
        AgentRunContextMetric metric = new AgentRunContextMetric();
        metric.setCallType(callType);
        metric.setMetricPhase(phase);
        metric.setCompressionStatus(compressionStatus);
        metric.setPromptTokens(promptTokens);
        metric.setInputBudgetTokens(budget);
        metric.setTrimmedMessageCount(trimmed);
        metric.setCompressedMessageCount(compressed);
        return metric;
    }
}
