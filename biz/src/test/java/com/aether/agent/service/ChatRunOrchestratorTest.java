package com.aether.agent.service;

import com.aether.exception.ServerException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Chat 运行锁编排测试。 */
class ChatRunOrchestratorTest {

    @Test
    void shouldReleaseLocalLockWhenDistributedLockIsNotAcquired() {
        ChatRunOrchestrator orchestrator = new ChatRunOrchestrator();
        // 编排器刻意从连接工厂直接取连接执行 SET NX，绕开 RedisTemplate 的事务绑定
        // （见 ChatRunOrchestrator#acquireDistributedLock 的注释），因此这里 mock 到命令层。
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands stringCommands = mock(RedisStringCommands.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(stringCommands);
        // 第一次抢锁失败，第二次成功。
        when(stringCommands.set(any(byte[].class), any(byte[].class), any(Expiration.class),
                any(RedisStringCommands.SetOption.class))).thenReturn(false, true);
        ReflectionTestUtils.setField(orchestrator, "redisConnectionFactory", connectionFactory);

        ChatRunContext context = new ChatRunContext("request-1", null, "user-1");
        assertThrows(ServerException.class, () -> orchestrator.executeSerialized(context, "conversation-1", null, null,
                ignored -> null));

        // 抢锁失败必须释放本地锁，否则同一会话后续请求会被永久拒绝。
        assertDoesNotThrow(() -> orchestrator.executeSerialized(context, "conversation-1", null, null,
                ignored -> null));
    }
}
