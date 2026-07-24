package com.aether.agent.service;

import com.aether.agent.model.ModelChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话模型上下文的 Redis 缓存。
 * 缓存异常只影响性能，调用方应继续使用数据库上下文。
 */
@Service
public class ConversationCacheService {
    private static final String CACHE_KEY_PREFIX = "agent:context:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public ConversationCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public List<ModelChatMessage> get(String conversationId) {
        try {
            Object cached = redisTemplate.opsForValue().get(key(conversationId));
            return cached instanceof List ? new ArrayList<ModelChatMessage>((List<ModelChatMessage>) cached) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(String conversationId, List<ModelChatMessage> context) {
        try {
            redisTemplate.opsForValue().set(key(conversationId), context, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // 缓存失败不影响聊天主流程。
        }
    }

    public void evict(String conversationId) {
        try {
            redisTemplate.delete(key(conversationId));
        } catch (Exception ignored) {
            // 缓存失效失败不影响聊天主流程。
        }
    }

    private String key(String conversationId) {
        return CACHE_KEY_PREFIX + conversationId;
    }
}
