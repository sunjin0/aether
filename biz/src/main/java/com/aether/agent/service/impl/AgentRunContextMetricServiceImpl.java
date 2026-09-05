package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.mapper.AgentRunContextMetricMapper;
import com.aether.agent.service.AgentRunContextMetricService;
import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentRunContextMetricServiceImpl
        extends ServiceImpl<AgentRunContextMetricMapper, AgentRunContextMetric>
        implements AgentRunContextMetricService {

    @Override
    public AgentContextOperationsMetricsVo operationsMetrics(Long sinceCreatedAt) {
        AgentContextOperationsMetricsVo result = new AgentContextOperationsMetricsVo();
        result.setSinceCreatedAt(sinceCreatedAt);
        // V178 retired agent_run_context_metric. Keep a stable empty response
        // for callers that have not yet removed this compatibility endpoint.
        return result;
    }
}
