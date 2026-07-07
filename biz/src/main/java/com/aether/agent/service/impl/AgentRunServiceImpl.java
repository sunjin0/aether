package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.mapper.AgentRunMapper;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 运行记录 Service 实现
 */
@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRun> implements AgentRunService {

    private static final int RUN_STATUS_SUCCESS = 0;
    private static final int RUN_STATUS_FAILED = 1;
    private static final int RUN_STATUS_TIMEOUT = 2;

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

        vo.setTotalCalls(totalCalls);
        vo.setSuccessCalls(successCalls);
        vo.setFailedCalls(failedCalls);
        vo.setTimeoutCalls(timeoutCalls);
        vo.setTotalPromptTokens(totalPromptTokens);
        vo.setTotalCompletionTokens(totalCompletionTokens);
        vo.setTotalTokens(totalTokens);
        vo.setAvgLatencyMs(latencySamples == 0L ? 0L : totalLatencyMs / latencySamples);
        vo.setErrorRate(totalCalls == 0L ? 0.0 : (failedCalls + timeoutCalls) * 1.0 / totalCalls);
        return vo;
    }

    private long safeLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
