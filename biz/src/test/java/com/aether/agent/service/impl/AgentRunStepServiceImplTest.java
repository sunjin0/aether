package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.mapper.AgentRunStepMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunStepServiceImplTest {

    @Test
    void saveIfAbsentReturnsFalseWhenUniqueConstraintRejectsDuplicate() {
        AgentRunStepMapper mapper = mock(AgentRunStepMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AgentRunStep.class)))
                .thenThrow(new DuplicateKeyException("duplicate event"));
        AgentRunStepServiceImpl service = new AgentRunStepServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        AgentRunStep step = new AgentRunStep();
        step.setRunId("run-1");
        step.setEventId("event-1");

        assertFalse(service.saveIfAbsent(step));
        verify(mapper).insert(org.mockito.ArgumentMatchers.any(AgentRunStep.class));
    }
}
