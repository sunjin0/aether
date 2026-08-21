package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.mapper.AgentRunContextMetricMapper;
import com.aether.agent.service.AgentRunContextMetricService;
import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentRunContextMetricServiceImpl
        extends ServiceImpl<AgentRunContextMetricMapper, AgentRunContextMetric>
        implements AgentRunContextMetricService {

    private static final double HIGH_PRESSURE_THRESHOLD = 80D;

    @Override
    public AgentContextOperationsMetricsVo operationsMetrics(Long sinceCreatedAt) {
        List<AgentRunContextMetric> metrics = list(Wrappers.lambdaQuery(AgentRunContextMetric.class)
                .ge(sinceCreatedAt != null, AgentRunContextMetric::getCreatedAt, sinceCreatedAt)
                .eq(AgentRunContextMetric::getDeleted, false));
        AgentContextOperationsMetricsVo result = new AgentContextOperationsMetricsVo();
        result.setSinceCreatedAt(sinceCreatedAt);
        double occupancySum = 0D;
        long occupancyCount = 0L;
        for (AgentRunContextMetric metric : metrics) {
            result.setTotalMetricCount(result.getTotalMetricCount() + 1);
            String callType = StringUtils.defaultIfBlank(metric.getCallType(), "ANSWER");
            increment(result.getByCallType(), callType);
            String compressionStatus = StringUtils.defaultIfBlank(metric.getCompressionStatus(), "UNKNOWN");
            increment(result.getByCompressionStatus(), compressionStatus);
            if (isCompletedRequestMetric(metric, callType)) {
                result.setCompletedRequestMetricCount(result.getCompletedRequestMetricCount() + 1);
                Double occupancy = occupancyPercent(metric);
                if (occupancy != null) {
                    occupancySum += occupancy;
                    occupancyCount++;
                    if (occupancy >= HIGH_PRESSURE_THRESHOLD) {
                        result.setHighPressureMetricCount(result.getHighPressureMetricCount() + 1);
                    }
                }
            }
            if ("COMPRESSION".equals(callType)) {
                result.setCompressionMetricCount(result.getCompressionMetricCount() + 1);
                if ("SYNC_COMPLETED".equals(compressionStatus)) {
                    result.setCompressionCompletedCount(result.getCompressionCompletedCount() + 1);
                } else if ("FAILED_FALLBACK".equals(compressionStatus)) {
                    result.setCompressionFailedFallbackCount(result.getCompressionFailedFallbackCount() + 1);
                } else if ("ASYNC_PENDING".equals(compressionStatus)) {
                    result.setCompressionPendingCount(result.getCompressionPendingCount() + 1);
                }
            }
            int trimmed = nullToZero(metric.getTrimmedMessageCount());
            if (trimmed > 0) {
                result.setTrimmedMetricCount(result.getTrimmedMetricCount() + 1);
                result.setTrimmedMessageCount(result.getTrimmedMessageCount() + trimmed);
            }
            int compressed = nullToZero(metric.getCompressedMessageCount());
            if (compressed > 0) {
                result.setCompressedMetricCount(result.getCompressedMetricCount() + 1);
                result.setCompressedMessageCount(result.getCompressedMessageCount() + compressed);
            }
        }
        if (occupancyCount > 0) {
            result.setAverageOccupancyPercent(Math.round(occupancySum * 100D / occupancyCount) / 100D);
        }
        return result;
    }

    private boolean isCompletedRequestMetric(AgentRunContextMetric metric, String callType) {
        return "FINAL".equals(metric.getMetricPhase())
                && ("ANSWER".equals(callType) || "DEEP_STEP".equals(callType));
    }

    private Double occupancyPercent(AgentRunContextMetric metric) {
        if (metric.getInputBudgetTokens() == null || metric.getInputBudgetTokens() <= 0) {
            return null;
        }
        Integer used = metric.getPromptTokens() == null ? metric.getEstimatedPromptTokens() : metric.getPromptTokens();
        if (used == null) {
            return null;
        }
        return Math.round(used * 10000D / metric.getInputBudgetTokens()) / 100D;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void increment(java.util.Map<String, Long> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0L) + 1);
    }
}
