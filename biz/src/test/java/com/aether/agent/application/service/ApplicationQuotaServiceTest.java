package com.aether.agent.application.service;

import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.exception.ServerException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ApplicationQuotaServiceTest {
    @Test void consumesWithinLimitAndRejectsOverflow() {
        AgentApplicationService applications = Mockito.mock(AgentApplicationService.class);
        RedisTemplate<String, Object> redis = Mockito.mock(RedisTemplate.class);
        ValueOperations<String, Object> operations = Mockito.mock(ValueOperations.class);
        AgentApplication application = new AgentApplication(); application.setMaxAgentCallsPerHour(1); application.setStatus(1);
        when(applications.requireActive("app-1")).thenReturn(application);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.increment(anyString())).thenReturn(1L).thenReturn(2L);
        ApplicationQuotaService service = new ApplicationQuotaService(applications, redis);
        assertDoesNotThrow(() -> service.consumeAgentCall("app-1"));
        assertThrows(ServerException.class, () -> service.consumeAgentCall("app-1"));
    }
}
