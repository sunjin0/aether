package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.mapper.AgentRunMapper;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceImplTest {

    @Mock
    private AgentRunMapper agentRunMapper;

    private AgentRunServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AgentRunServiceImpl();
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, agentRunMapper);
    }

    @Test
    void statisticsAggregatesRuns() {
        AgentRun success = run(0, 10, 20, 30, 100);
        AgentRun failed = run(1, 1, null, 1, 300);
        AgentRun timeout = run(2, null, 4, 4, null);

        when(agentRunMapper.selectList(any())).thenReturn(Arrays.asList(success, failed, timeout));

        AgentRunStatisticsVo result = service.statistics("agent-1", 1000L, 2000L);

        assertEquals("agent-1", result.getAgentDefinitionId());
        assertEquals(3L, result.getTotalCalls());
        assertEquals(1L, result.getSuccessCalls());
        assertEquals(1L, result.getFailedCalls());
        assertEquals(1L, result.getTimeoutCalls());
        assertEquals(11L, result.getTotalPromptTokens());
        assertEquals(24L, result.getTotalCompletionTokens());
        assertEquals(35L, result.getTotalTokens());
        assertEquals(200L, result.getAvgLatencyMs());
        assertEquals(2.0 / 3.0, result.getErrorRate());
    }

    private AgentRun run(Integer status, Integer promptTokens, Integer completionTokens,
                         Integer totalTokens, Integer latencyMs) {
        AgentRun run = new AgentRun();
        run.setStatus(status);
        run.setPromptTokens(promptTokens);
        run.setCompletionTokens(completionTokens);
        run.setTotalTokens(totalTokens);
        run.setLatencyMs(latencyMs);
        return run;
    }
}
