package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ConversationMemoryService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 实现会话Memory业务服务。
 */
@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryServiceImpl.class);
    private static final String MEMORY_KEY_PREFIX = "agent:memory:";
    private static final long MEMORY_TTL_HOURS = 72;

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建 {@code ConversationMemoryServiceImpl} 实例。
     */
    public ConversationMemoryServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 处理storeMemory。
     */
    @Override
    public void storeMemory(String conversationId, String userId, String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        String key = MEMORY_KEY_PREFIX + userId + ":" + conversationId + ":" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, content, MEMORY_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 处理retrieveRelevantMemories。
     */
    @Override
    public List<String> retrieveRelevantMemories(String userId, String query, int topK) {
        return Collections.emptyList();
    }

    /**
     * 删除会话Memories。
     */
    @Override
    public void deleteConversationMemories(String conversationId) {
    }

    /**
     * 处理storeSegment。
     */
    @Override
    public void storeSegment(String conversationId, String userId, List<String> messages,
                             AgentDefinition agent, ModelProvider provider) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String combined = String.join("\n", messages);
        if (StringUtils.isBlank(combined)) {
            return;
        }
        String key = MEMORY_KEY_PREFIX + userId + ":" + conversationId + ":" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, combined, MEMORY_TTL_HOURS, TimeUnit.HOURS);
    }
}
