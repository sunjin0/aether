package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.mapper.AgentRunContextMetricMapper;
import com.aether.agent.service.AgentRunContextMetricService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentRunContextMetricServiceImpl
        extends ServiceImpl<AgentRunContextMetricMapper, AgentRunContextMetric>
        implements AgentRunContextMetricService {
}
