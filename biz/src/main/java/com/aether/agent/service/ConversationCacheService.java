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
    private static final int MAX_CONTEXT_MESSAGES = 21;
    private static final int MAX_HISTORY_MESSAGES = 20;

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

    /** 追加一条消息，并保留系统提示和最近消息。 */
    public void append(String conversationId, ModelChatMessage message) {
        List<ModelChatMessage> context = get(conversationId);
        if (context == null) {
            return;
        }
        context.add(message);
        put(conversationId, trim(context));
    }

    private List<ModelChatMessage> trim(List<ModelChatMessage> context) {
        if (context.size() <= MAX_CONTEXT_MESSAGES) {
            return context;
        }
        if ("system".equals(context.get(0).getRole())) {
            List<ModelChatMessage> trimmed = new ArrayList<ModelChatMessage>();
            trimmed.add(context.get(0));
            trimmed.addAll(context.subList(Math.max(1, context.size() - MAX_HISTORY_MESSAGES), context.size()));
            return trimmed;
        }
        return new ArrayList<ModelChatMessage>(context.subList(context.size() - MAX_CONTEXT_MESSAGES, context.size()));
    }

    private String key(String conversationId) {
        return CACHE_KEY_PREFIX + conversationId;
    }
}
