package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClientFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 生成、缓存并异步刷新会话历史摘要。 */
@Service
public class ConversationSummaryService {
    private static final String SUMMARY_KEY_PREFIX = "agent:summary:";
    private static final long SUMMARY_TTL_HOURS = 24;
    private static final int SUMMARY_MESSAGE_LIMIT = 30;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ModelClientFactory modelClientFactory;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public ConversationSummaryService(RedisTemplate<String, Object> redisTemplate,
                                      ModelClientFactory modelClientFactory) {
        this.redisTemplate = redisTemplate;
        this.modelClientFactory = modelClientFactory;
    }

    /**
     * 优先返回缓存摘要；缓存缺失时异步创建，当前请求继续使用最近消息。
     */
    public String getOrCreate(String conversationId, List<AgentMessage> oldMessages,
                              AgentDefinition agent, ModelProvider provider) {
        String cacheKey = SUMMARY_KEY_PREFIX + conversationId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached.toString();
            }
        } catch (Exception ignored) {
            // Redis 不可用时仍可异步生成，下一轮继续尝试写入缓存。
        }

        final List<AgentMessage> snapshot = new ArrayList<AgentMessage>(oldMessages);
        CompletableFuture.runAsync(() -> saveSummary(cacheKey, snapshot, agent, provider), executor);
        return "";
    }

    private void saveSummary(String cacheKey, List<AgentMessage> messages,
                             AgentDefinition agent, ModelProvider provider) {
        String summary = generate(messages, agent, provider);
        if (StringUtils.isBlank(summary)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, summary, SUMMARY_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception ignored) {
            // 摘要写缓存失败不影响当前或后续聊天。
        }
    }

    private String generate(List<AgentMessage> messages, AgentDefinition agent, ModelProvider provider) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        try {
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setMessages(Collections.singletonList(new ModelChatMessage("user", buildPrompt(messages))));
            ModelChatResponse response = modelClientFactory.getClient(provider).chat(request);
            return response == null ? "" : StringUtils.defaultString(response.getContent());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildPrompt(List<AgentMessage> messages) {
        StringBuilder prompt = new StringBuilder("请将以下对话历史总结为关键要点，保留重要信息、用户意图和上下文，200字以内：\n\n");
        int start = Math.max(0, messages.size() - SUMMARY_MESSAGE_LIMIT);
        for (int i = start; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            prompt.append("user".equals(message.getRole()) ? "用户: " : "助手: ")
                    .append(message.getContent()).append("\n\n");
        }
        return prompt.toString();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
