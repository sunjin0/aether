package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.mapper.AgentRunMapper;
import com.aether.agent.service.AgentRunContextMetricService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 运行记录 Service 实现
 */
@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRun> implements AgentRunService {

    private static final int RUN_STATUS_SUCCESS = 0;
    private static final int RUN_STATUS_FAILED = 1;
    private static final int RUN_STATUS_TIMEOUT = 2;
    private final AgentRunContextMetricService contextMetricService;

    public AgentRunServiceImpl(AgentRunContextMetricService contextMetricService) {
        this.contextMetricService = contextMetricService;
    }

    /**
     * 处理statistics。
     */
    @Override
    public AgentRunStatisticsVo statistics(String agentDefinitionId, Long startTime, Long endTime) {
        List<AgentRun> runs = list(Wrappers.lambdaQuery(AgentRun.class)
                .eq(StringUtils.isNotBlank(agentDefinitionId), AgentRun::getAgentDefinitionId, agentDefinitionId)
                .ge(startTime != null, AgentRun::getCreatedAt, startTime)
                .le(endTime != null, AgentRun::getCreatedAt, endTime)
                .eq(AgentRun::getDeleted, false));

        AgentRunStatisticsVo vo = new AgentRunStatisticsVo();
        vo.setAgentDefinitionId(agentDefinitionId);

        long totalCalls = 0L;
        long successCalls = 0L;
        long failedCalls = 0L;
        long timeoutCalls = 0L;
        long totalPromptTokens = 0L;
        long totalCompletionTokens = 0L;
        long totalTokens = 0L;
        long totalCachedPromptTokens = 0L;
        long totalUncachedPromptTokens = 0L;
        long cacheObservedCallCount = 0L;
        long cacheUnobservedCallCount = 0L;
        long cacheZeroHitCallCount = 0L;
        long totalLatencyMs = 0L;
        long latencySamples = 0L;

        for (AgentRun run : runs) {
            totalCalls++;
            Integer status = run.getStatus();
            if (Integer.valueOf(RUN_STATUS_SUCCESS).equals(status)) {
                successCalls++;
            } else if (Integer.valueOf(RUN_STATUS_FAILED).equals(status)) {
                failedCalls++;
            } else if (Integer.valueOf(RUN_STATUS_TIMEOUT).equals(status)) {
                timeoutCalls++;
            }

            totalPromptTokens += safeLong(run.getPromptTokens());
            totalCompletionTokens += safeLong(run.getCompletionTokens());
            totalTokens += safeLong(run.getTotalTokens());
            if (run.getLatencyMs() != null) {
                totalLatencyMs += run.getLatencyMs();
                latencySamples++;
            }
        }

        if (!runs.isEmpty()) {
            List<String> runIds = runs.stream().map(AgentRun::getId).filter(id -> id != null)
                    .collect(Collectors.toList());
            if (!runIds.isEmpty()) {
                List<AgentRunContextMetric> metrics = contextMetricService.list(Wrappers.lambdaQuery(AgentRunContextMetric.class)
                        .in(AgentRunContextMetric::getRunId, runIds)
                        .eq(AgentRunContextMetric::getMetricPhase, "FINAL")
                        .eq(AgentRunContextMetric::getDeleted, false));
                for (AgentRunContextMetric metric : metrics) {
                    boolean cacheObserved = metric.getCachedPromptTokens() != null
                            || metric.getUncachedPromptTokens() != null
                            || metric.getPromptCacheHitRate() != null;
                    if (cacheObserved) {
                        cacheObservedCallCount++;
                        if (safeLong(metric.getCachedPromptTokens()) == 0L) {
                            cacheZeroHitCallCount++;
                        }
                    } else {
                        cacheUnobservedCallCount++;
                    }
                    totalCachedPromptTokens += safeLong(metric.getCachedPromptTokens());
                    totalUncachedPromptTokens += safeLong(metric.getUncachedPromptTokens());
                }
            }
        }

        vo.setTotalCalls(totalCalls);
        vo.setSuccessCalls(successCalls);
        vo.setFailedCalls(failedCalls);
        vo.setTimeoutCalls(timeoutCalls);
        vo.setTotalPromptTokens(totalPromptTokens);
        vo.setTotalCompletionTokens(totalCompletionTokens);
        vo.setTotalTokens(totalTokens);
        vo.setTotalCachedPromptTokens(totalCachedPromptTokens);
        vo.setCacheObservedCallCount(cacheObservedCallCount);
        vo.setCacheUnobservedCallCount(cacheUnobservedCallCount);
        vo.setCacheZeroHitCallCount(cacheZeroHitCallCount);
        long observedPromptTokens = totalCachedPromptTokens + totalUncachedPromptTokens;
        vo.setPromptCacheHitRate(observedPromptTokens == 0L ? 0D
                : Math.round(totalCachedPromptTokens * 10000D / observedPromptTokens) / 100D);
        vo.setAvgLatencyMs(latencySamples == 0L ? 0L : totalLatencyMs / latencySamples);
        vo.setErrorRate(totalCalls == 0L ? 0.0 : (failedCalls + timeoutCalls) * 1.0 / totalCalls);
        return vo;
    }

    /**
     * 处理safeLong。
     */
    private long safeLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
