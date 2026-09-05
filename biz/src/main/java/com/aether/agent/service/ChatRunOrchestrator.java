package com.aether.agent.service;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import com.aether.exception.ServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 普通聊天、流式聊天和审批恢复共用的执行边界。 */
@Service
public class ChatRunOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ChatRunOrchestrator.class);
    private static final String LOCK_PREFIX = "AgentConversationLock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end", Long.class);
    private static final long LOCK_LEASE_SECONDS = 120L;
    private static final long LOCK_RENEW_SECONDS = 30L;
    private final ConcurrentHashMap<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService lockRenewalExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-run-lock-renewal");
        thread.setDaemon(true);
        return thread;
    });

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
        boolean locked;
        try {
            // Allow the previous stream callback a short hand-off window before
            // reporting a conflict to the client.
            locked = localLock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }
        if (!locked) {
            throw new ServerException(409, "当前会话已有请求正在执行，请稍后重试");
        }
        try {
            String distributedToken = acquireDistributedLock(lockKey);
            ScheduledFuture<?> renewal = renewDistributedLock(lockKey, distributedToken);
            try {
                return execute(context, sink, beforeRun, body);
            } finally {
                if (renewal != null) renewal.cancel(false);
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
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + lockKey, token,
                LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ServerException(409, "当前会话已有请求正在执行，请稍后重试");
        }
        return token;
    }

    /** 正常运行期间续租；异常退出时短租约会自动释放，避免遗留锁阻塞下一轮对话。 */
    private ScheduledFuture<?> renewDistributedLock(String lockKey, String token) {
        if (stringRedisTemplate == null || token == null) return null;
        return lockRenewalExecutor.scheduleAtFixedRate(() -> {
            try {
                Long renewed = stringRedisTemplate.execute(RENEW_LOCK, Collections.singletonList(LOCK_PREFIX + lockKey),
                        token, String.valueOf(TimeUnit.SECONDS.toMillis(LOCK_LEASE_SECONDS)));
                if (!Long.valueOf(1L).equals(renewed)) {
                    log.warn("会话锁续租失败，运行将由取消或租约到期恢复: key={}", LOCK_PREFIX + lockKey);
                }
            } catch (RuntimeException e) {
                log.warn("会话锁续租异常，运行将由取消或租约到期恢复: key={}", LOCK_PREFIX + lockKey, e);
            }
        }, LOCK_RENEW_SECONDS, LOCK_RENEW_SECONDS, TimeUnit.SECONDS);
    }

    /** 仅持有同一随机令牌的调用方可以释放锁，避免租约过期后的误删。 */
    private void releaseDistributedLock(String lockKey, String token) {
        if (stringRedisTemplate == null || token == null) return;
        try {
            Long released = stringRedisTemplate.execute(RELEASE_LOCK,
                    Collections.singletonList(LOCK_PREFIX + lockKey), token);
            if (!Long.valueOf(1L).equals(released)) {
                log.debug("会话锁未由当前请求释放（可能已过期或被替换）: key={}", LOCK_PREFIX + lockKey);
            }
        } catch (RuntimeException e) {
            // Redis 短暂中断不应让业务线程带着未释放锁结束；下一次请求
            // 可依赖租约恢复，同时记录 key 便于排查。
            log.warn("会话锁释放失败，将等待租约自动过期: key={}", LOCK_PREFIX + lockKey, e);
        }
    }
}
