package com.aether.agent.service;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import com.aether.exception.ServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 普通聊天、流式聊天和审批恢复共用的执行边界。 */
@Service
public class ChatRunOrchestrator {
    private static final String LOCK_PREFIX = "AgentConversationLock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private final ConcurrentHashMap<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    /**
     * 会话锁不能参与业务数据库事务；否则 SET NX 会在事务提交时才执行，
     * 调用方已经返回冲突时反而留下孤儿锁。
     */
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;
    public interface ResponseSink {
        /** 通知运行已接收。 */
        default void accepted(ChatRunContext context) { }
        /** 通知运行进度。 */
        default void progress(String stage, String message) { }
        /** 通知运行完成。 */
        default void completed(Object result) { }
        /** 通知运行失败。 */
        default void failed(Throwable error) { }
    }

    /** 执行统一的取消检查、前置处理、业务主体和结果通知。 */
    public <T> T execute(ChatRunContext context, ResponseSink sink,
                         Consumer<ChatRunContext> beforeRun,
                         java.util.function.Function<ChatRunContext, T> body) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(body, "body");
        if (sink != null) sink.accepted(context);
        try {
            context.checkCancelled();
            if (beforeRun != null) beforeRun.accept(context);
            context.checkCancelled();
            T result = body.apply(context);
            context.checkCancelled();
            if (sink != null) sink.completed(result);
            return result;
        } catch (RuntimeException error) {
            if (sink != null) sink.failed(error);
            throw error;
        } catch (Error error) {
            if (sink != null) sink.failed(error);
            throw error;
        }
    }

    /**
     * 在同一会话串行边界内执行统一生命周期。不同会话仍可并行，避免多实例消息乱序。
     */
    public <T> T executeSerialized(ChatRunContext context, String lockKey, ResponseSink sink,
                                   Consumer<ChatRunContext> beforeRun,
                                   java.util.function.Function<ChatRunContext, T> body) {
        Objects.requireNonNull(lockKey, "lockKey");
        ReentrantLock localLock = localLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());
        if (!localLock.tryLock()) {
            throw new ServerException(409, "当前会话已有请求正在执行，请稍后重试");
        }
        try {
            String distributedToken = acquireDistributedLock(lockKey);
            try {
                return execute(context, sink, beforeRun, body);
            } finally {
                releaseDistributedLock(lockKey, distributedToken);
            }
        } finally {
            localLock.unlock();
            localLocks.remove(lockKey, localLock);
        }
    }

    /** 通过 SET NX 和有限租约抢占跨实例会话锁。 */
    private String acquireDistributedLock(String lockKey) {
        if (stringRedisTemplate == null) return null;
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + lockKey, token, 30, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ServerException(409, "当前会话已有请求正在执行，请稍后重试");
        }
        return token;
    }

    /** 仅持有同一随机令牌的调用方可以释放锁，避免租约过期后的误删。 */
    private void releaseDistributedLock(String lockKey, String token) {
        if (stringRedisTemplate == null || token == null) return;
        stringRedisTemplate.execute(RELEASE_LOCK, Collections.singletonList(LOCK_PREFIX + lockKey), token);
    }
}
