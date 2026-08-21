package com.aether.agent.service;

import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.vo.AgentContextOperationsMetricsVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentRunContextMetricService extends IService<AgentRunContextMetric> {
    AgentContextOperationsMetricsVo operationsMetrics(Long sinceCreatedAt);
}
