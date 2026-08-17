package com.aether.agent.service;

import com.aether.agent.model.ModelChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话模型上下文的 Redis 缓存。
 * 缓存异常只影响性能，调用方应继续使用数据库上下文。
 */
@Service
public class ConversationCacheService {
    private static final Logger log = LoggerFactory.getLogger(ConversationCacheService.class);
    private static final String CACHE_KEY_PREFIX = "agent:context:v2:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 {@code ConversationCacheService} 实例。
     */
    public ConversationCacheService(@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取当前请求。
     */
    @SuppressWarnings("unchecked")
    public List<ModelChatMessage> get(String conversationId) {
        try {
            List<Object> cached = redisTemplate.opsForList().range(key(conversationId), 0, -1);
            if (cached == null || cached.isEmpty()) {
                return null;
            }
            List<ModelChatMessage> result = new ArrayList<ModelChatMessage>(cached.size());
            for (Object obj : cached) {
                if (obj instanceof ModelChatMessage) {
                    result.add((ModelChatMessage) obj);
                }
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("读取会话缓存失败: conversationId={}", conversationId, e);
            return null;
        }
    }

    /**
     * 处理put。
     */
    public void put(String conversationId, List<ModelChatMessage> context) {
        try {
            String cacheKey = key(conversationId);
            redisTemplate.delete(cacheKey);
            if (context != null && !context.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(cacheKey, context.toArray());
                redisTemplate.expire(cacheKey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("写入会话缓存失败: conversationId={}", conversationId, e);
        }
    }

    private static final long MAX_CACHE_SIZE = 20;

    /**
     * 处理append。
     */
    public void append(String conversationId, ModelChatMessage message) {
        try {
            String cacheKey = key(conversationId);
            Long size = redisTemplate.opsForList().rightPush(cacheKey, message);
            if (size != null && size == 1) {
                redisTemplate.expire(cacheKey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            redisTemplate.opsForList().trim(cacheKey, -MAX_CACHE_SIZE, -1);
        } catch (Exception e) {
            log.warn("追加会话缓存失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 处理evict。
     */
    public void evict(String conversationId) {
        try {
            redisTemplate.delete(key(conversationId));
        } catch (Exception e) {
            log.warn("清理会话缓存失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 处理size。
     */
    public long size(String conversationId) {
        try {
            Long size = redisTemplate.opsForList().size(key(conversationId));
            return size != null ? size : 0;
        } catch (Exception e) {
            log.warn("读取会话缓存大小失败: conversationId={}", conversationId, e);
            return 0;
        }
    }

    /**
     * 获取Recent。
     */
    public List<ModelChatMessage> getRecent(String conversationId, int count) {
        try {
            List<Object> cached = redisTemplate.opsForList().range(key(conversationId), -count, -1);
            if (cached == null || cached.isEmpty()) {
                return Collections.emptyList();
            }
            List<ModelChatMessage> result = new ArrayList<ModelChatMessage>(cached.size());
            for (Object obj : cached) {
                if (obj instanceof ModelChatMessage) {
                    result.add((ModelChatMessage) obj);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("读取会话最近缓存失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 处理key。
     */
    private String key(String conversationId) {
        return CACHE_KEY_PREFIX + conversationId;
    }
}
