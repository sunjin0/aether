package com.aether.agent.service.impl;

import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证上下文运营指标聚合。
 */
class AgentRunContextMetricServiceImplTest {

    /**
     * V178 退役 agent_run_context_metric 后，该端点不再聚合历史快照，
     * 只回显查询下界，其余字段保持稳定的空值（见实现中的兼容性说明）。
     */
    @Test
    void operationsMetricsReturnsStableEmptyResponseAfterV178() {
        AgentRunContextMetricServiceImpl service = new AgentRunContextMetricServiceImpl();

        AgentContextOperationsMetricsVo result = service.operationsMetrics(123L);

        assertEquals(Long.valueOf(123L), result.getSinceCreatedAt());
        assertEquals(Long.valueOf(0L), result.getTotalMetricCount());
        assertEquals(Long.valueOf(0L), result.getCompletedRequestMetricCount());
        assertEquals(0D, result.getAverageOccupancyPercent());
        assertEquals(Long.valueOf(0L), result.getCacheObservedMetricCount());
        assertEquals(0D, result.getAveragePromptCacheHitRate());
        assertEquals(Long.valueOf(0L), result.getTotalCachedPromptTokens());
        assertEquals(Long.valueOf(0L), result.getTotalUncachedPromptTokens());
        assertEquals(Long.valueOf(0L), result.getHighPressureMetricCount());
        assertEquals(Long.valueOf(0L), result.getCompressionMetricCount());
        assertEquals(Long.valueOf(0L), result.getCompressionCompletedCount());
        assertEquals(Long.valueOf(0L), result.getCompressionFailedFallbackCount());
        assertEquals(Long.valueOf(0L), result.getTrimmedMetricCount());
        assertEquals(Long.valueOf(0L), result.getTrimmedMessageCount());
        assertEquals(Long.valueOf(0L), result.getCompressedMetricCount());
        assertEquals(Long.valueOf(0L), result.getCompressedMessageCount());
        assertTrue(result.getByCallType().isEmpty());
        assertTrue(result.getByCompressionStatus().isEmpty());
        assertFalse(result.getLatencyAvailable());
    }

    /**
     * 查询下界为空时同样原样回显，不抛异常。
     */
    @Test
    void operationsMetricsEchoesNullLowerBound() {
        AgentRunContextMetricServiceImpl service = new AgentRunContextMetricServiceImpl();

        AgentContextOperationsMetricsVo result = service.operationsMetrics(null);

        assertNull(result.getSinceCreatedAt());
        assertEquals(Long.valueOf(0L), result.getTotalMetricCount());
    }
}
