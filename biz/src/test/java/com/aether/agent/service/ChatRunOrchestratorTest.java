package com.aether.agent.service;

import com.aether.exception.ServerException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Chat 运行锁编排测试。 */
class ChatRunOrchestratorTest {

    @Test
    void shouldReleaseLocalLockWhenDistributedLockIsNotAcquired() {
        ChatRunOrchestrator orchestrator = new ChatRunOrchestrator();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false, true);
        ReflectionTestUtils.setField(orchestrator, "stringRedisTemplate", redisTemplate);

        ChatRunContext context = new ChatRunContext("request-1", null, "user-1");
        assertThrows(ServerException.class, () -> orchestrator.executeSerialized(context, "conversation-1", null, null,
                ignored -> null));

        assertDoesNotThrow(() -> orchestrator.executeSerialized(context, "conversation-1", null, null,
                ignored -> null));
    }
}
