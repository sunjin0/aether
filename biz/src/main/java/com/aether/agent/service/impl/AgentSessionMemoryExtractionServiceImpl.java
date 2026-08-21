package com.aether.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AgentMessageContentResolver;
import com.aether.agent.service.AgentSessionMemoryExtractionService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentSessionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 实现会话记忆自动提取服务。
 */
@Service
public class AgentSessionMemoryExtractionServiceImpl implements AgentSessionMemoryExtractionService {
    private static final Logger log = LoggerFactory.getLogger(AgentSessionMemoryExtractionServiceImpl.class);
    private static final int MAX_CANDIDATE_LENGTH = 500;
    private static final int MAX_CANDIDATES_PER_TURN = 3;
    private static final int MIN_MODEL_CONFIDENCE = 60;
    private static final String EXTRACTOR_VERSION = "session-memory-v1";
    private static final String[] FORBIDDEN_CONTENT_MARKERS = {
            "password", "passwd", "secret", "api_key", "apikey", "access_token", "private_key",
            "密码", "密钥", "令牌", "私钥"
    };

    private final AgentSessionService sessionService;
    private final AgentSessionMemoryService memoryService;
    private final ModelClientFactory modelClientFactory;

    /**
     * 创建 {@code AgentSessionMemoryExtractionServiceImpl} 实例。
     */
    public AgentSessionMemoryExtractionServiceImpl(AgentSessionService sessionService,
                                                   AgentSessionMemoryService memoryService) {
        this(sessionService, memoryService, null);
    }

    /**
     * 创建 {@code AgentSessionMemoryExtractionServiceImpl} 实例。
     */
    public AgentSessionMemoryExtractionServiceImpl(AgentSessionService sessionService,
                                                   AgentSessionMemoryService memoryService,
                                                   ModelClientFactory modelClientFactory) {
        this.sessionService = sessionService;
        this.memoryService = memoryService;
        this.modelClientFactory = modelClientFactory;
    }

    /**
     * 在一轮消息完成后异步提取可进入会话记忆的候选项。
     */
    @Override
    @Async("asyncPoolTaskExecutor")
    public void extractAsync(String userId, String conversationId,
                             AgentMessage userMessage, AgentMessage assistantMessage,
                             AgentDefinition agent, ModelProvider provider) {
        try {
            extract(userId, conversationId, userMessage, assistantMessage, agent, provider);
        } catch (Exception e) {
            log.warn("会话记忆提取失败: conversationId={}, messageId={}, reason={}",
                    conversationId, userMessage == null ? null : userMessage.getId(), e.getMessage());
        }
    }

    /**
     * 执行提取。
     */
    void extract(String userId, String conversationId, AgentMessage userMessage, AgentDefinition agent) {
        extract(userId, conversationId, userMessage, null, agent, null);
    }

    /**
     * 执行提取。
     */
    void extract(String userId, String conversationId, AgentMessage userMessage,
                 AgentMessage assistantMessage, AgentDefinition agent, ModelProvider provider) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(conversationId)
                || userMessage == null || !"user".equals(userMessage.getRole())) {
            return;
        }
        String content = AgentMessageContentResolver.getEffectiveContent(userMessage);
        if (StringUtils.isBlank(content) || containsForbiddenContent(content)) {
            return;
        }
        List<Candidate> candidates = modelCandidates(content, userMessage, assistantMessage, agent, provider);
        if (candidates.isEmpty()) {
            candidates = ruleCandidates(content, userMessage.getId());
        }
        if (candidates.isEmpty()) {
            return;
        }
        AgentSession session = sessionService.getOrCreate(conversationId, userId,
                agent == null ? null : agent.getId());
        Set<String> existing = activeMemoryFingerprints(session.getId());
        Set<String> existingHashes = activeCandidateHashes(session.getId());
        int saved = 0;
        for (Candidate candidate : candidates) {
            String fingerprint = fingerprint(candidate.type, candidate.content);
            if (existing.contains(fingerprint) || existingHashes.contains(candidate.candidateHash)) {
                continue;
            }
            memoryService.recordExtractedMemory(session.getId(), candidate.type, candidate.content,
                    userMessage.getId(), candidate.confidence, candidate.sensitivityLevel,
                    EXTRACTOR_VERSION, candidate.candidateHash, candidate.sourceEventRange);
            existing.add(fingerprint);
            existingHashes.add(candidate.candidateHash);
            saved++;
            if (saved >= MAX_CANDIDATES_PER_TURN) {
                break;
            }
        }
    }

    /**
     * 调用模型生成候选记忆。
     */
    private List<Candidate> modelCandidates(String content, AgentMessage userMessage,
                                            AgentMessage assistantMessage,
                                            AgentDefinition agent, ModelProvider provider) {
        if (modelClientFactory == null || provider == null) {
            return Collections.emptyList();
        }
        try {
            ModelChatRequest request = new ModelChatRequest();
            request.setProvider(provider);
            request.setAgent(agent);
            request.setTemperature(BigDecimal.ZERO);
            request.setMaxCompletionTokens(800);
            Map<String, Object> responseFormat = new HashMap<String, Object>();
            responseFormat.put("type", "json_object");
            request.setResponseFormat(responseFormat);
            request.setMessages(Collections.singletonList(new ModelChatMessage("user",
                    buildExtractionPrompt(content, userMessage, assistantMessage))));

            ModelClient client = modelClientFactory.getClient(provider);
            ModelChatResponse response = client.chat(request);
            return parseModelCandidates(response == null ? null : response.getContent(), userMessage.getId());
        } catch (Exception e) {
            log.warn("模型会话记忆候选提取失败，回退规则提取: messageId={}, reason={}",
                    userMessage.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建提取提示词。
     */
    private String buildExtractionPrompt(String content, AgentMessage userMessage, AgentMessage assistantMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extract durable session memory candidates from the latest turn.\n");
        prompt.append("Return only JSON: {\"memories\":[{\"type\":\"GOAL|CONSTRAINT|FACT|DECISION|TODO|ARTIFACT\",");
        prompt.append("\"content\":\"...\",\"confidence\":0-100,\"sensitivityLevel\":\"NORMAL|SENSITIVE\",");
        prompt.append("\"sourceEventIds\":[\"").append(StringUtils.defaultString(userMessage.getId())).append("\"]}]}\n");
        prompt.append("Rules:\n");
        prompt.append("- Only extract facts, goals, constraints, decisions, todos, or artifacts explicitly grounded in the user message.\n");
        prompt.append("- Do not extract preferences; preferences are handled by another subsystem.\n");
        prompt.append("- Exclude passwords, tokens, keys, secrets, private keys, or credentials.\n");
        prompt.append("- Use an empty memories array when nothing durable should be saved.\n");
        prompt.append("- sourceEventIds must include the latest user message id.\n\n");
        prompt.append("Latest user message id: ").append(StringUtils.defaultString(userMessage.getId())).append("\n");
        prompt.append("Latest user message:\n").append(StringUtils.abbreviate(content, 2000)).append("\n");
        if (assistantMessage != null) {
            prompt.append("Latest assistant message:\n")
                    .append(StringUtils.abbreviate(
                            StringUtils.defaultString(AgentMessageContentResolver.getEffectiveContent(assistantMessage)), 1000))
                    .append("\n");
        }
        return prompt.toString();
    }

    /**
     * 解析并校验模型候选。
     */
    private List<Candidate> parseModelCandidates(String response, String sourceMessageId) {
        List<Candidate> result = new ArrayList<Candidate>();
        if (StringUtils.isBlank(response)) {
            return result;
        }
        try {
            JSONObject payload = JSON.parseObject(unwrapJsonCodeFence(response));
            JSONArray memories = payload == null ? null : payload.getJSONArray("memories");
            if (memories == null) {
                return result;
            }
            Set<String> seen = new LinkedHashSet<String>();
            for (int i = 0; i < memories.size() && result.size() < MAX_CANDIDATES_PER_TURN; i++) {
                JSONObject item = memories.getJSONObject(i);
                Candidate candidate = normalizeModelCandidate(item, sourceMessageId);
                if (candidate == null) {
                    continue;
                }
                String fingerprint = fingerprint(candidate.type, candidate.content);
                if (seen.add(fingerprint)) {
                    result.add(candidate);
                }
            }
        } catch (Exception e) {
            log.warn("忽略不合规的会话记忆模型输出: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 标准化模型候选。
     */
    private Candidate normalizeModelCandidate(JSONObject item, String sourceMessageId) {
        if (item == null) {
            return null;
        }
        String type = StringUtils.upperCase(StringUtils.trimToEmpty(item.getString("type")));
        String content = normalizeStatement(item.getString("content"));
        Integer confidence = item.getInteger("confidence");
        String sensitivityLevel = StringUtils.upperCase(
                StringUtils.defaultIfBlank(item.getString("sensitivityLevel"), "NORMAL"));
        JSONArray sourceEventIds = item.getJSONArray("sourceEventIds");
        if (!isAutoExtractableType(type) || StringUtils.isBlank(content)
                || content.length() < 6 || content.length() > MAX_CANDIDATE_LENGTH
                || containsForbiddenContent(content)
                || confidence == null || confidence < MIN_MODEL_CONFIDENCE || confidence > 100
                || (!"NORMAL".equals(sensitivityLevel) && !"SENSITIVE".equals(sensitivityLevel))
                || !containsSourceMessage(sourceEventIds, sourceMessageId)) {
            return null;
        }
        String sourceEventRange = sourceEventRange(sourceEventIds);
        return new Candidate(type, content, confidence, sensitivityLevel, sourceEventRange,
                candidateHash(sourceMessageId, type, content));
    }

    /**
     * 生成规则候选记忆。
     */
    private List<Candidate> ruleCandidates(String content, String sourceMessageId) {
        List<Candidate> result = new ArrayList<Candidate>();
        Set<String> seen = new LinkedHashSet<String>();
        String[] lines = content.split("[\\r\\n。！？!?]+");
        for (String raw : lines) {
            String line = normalizeStatement(raw);
            if (StringUtils.isBlank(line) || line.length() < 6 || line.length() > MAX_CANDIDATE_LENGTH) {
                continue;
            }
            Candidate candidate = classify(line);
            if (candidate == null || containsForbiddenContent(candidate.content)) {
                continue;
            }
            candidate = candidate.withSource(sourceMessageId, candidateHash(sourceMessageId, candidate.type, candidate.content));
            String fingerprint = fingerprint(candidate.type, candidate.content);
            if (seen.add(fingerprint)) {
                result.add(candidate);
            }
            if (result.size() >= MAX_CANDIDATES_PER_TURN) {
                break;
            }
        }
        return result;
    }

    /**
     * 分类候选记忆。
     */
    private Candidate classify(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "preference", "prefer", "喜欢", "偏好")) {
            return null;
        }
        if (containsAny(lower, "目标", "goal is", "goal:", "目标是")) {
            return new Candidate("GOAL", stripLeadIn(line), 80);
        }
        if (containsAny(lower, "必须", "需要", "要求", "约束", "constraint", "must", "required")) {
            return new Candidate("CONSTRAINT", stripLeadIn(line), 85);
        }
        if (containsAny(lower, "决定", "已确定", "采用", "decision", "decided")) {
            return new Candidate("DECISION", stripLeadIn(line), 85);
        }
        if (containsAny(lower, "待办", "todo", "下一步", "还需要")) {
            return new Candidate("TODO", stripLeadIn(line), 75);
        }
        if (containsAny(lower, "事实", "项目使用", "项目是", "当前使用", "uses ", "is ")) {
            return new Candidate("FACT", stripLeadIn(line), 70);
        }
        if (containsAny(lower, "产物", "artifact", "文件", "文档")) {
            return new Candidate("ARTIFACT", stripLeadIn(line), 70);
        }
        return null;
    }

    /**
     * 获取活跃记忆指纹。
     */
    private Set<String> activeMemoryFingerprints(String sessionId) {
        Set<String> result = new LinkedHashSet<String>();
        List<AgentSessionMemory> memories = memoryService.list(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .eq(AgentSessionMemory::getStatus, AgentSessionMemory.STATUS_ACTIVE));
        if (memories != null) {
            for (AgentSessionMemory memory : memories) {
                result.add(fingerprint(memory.getMemoryType(), memory.getContent()));
            }
        }
        return result;
    }

    /**
     * 获取活跃记忆候选哈希。
     */
    private Set<String> activeCandidateHashes(String sessionId) {
        Set<String> result = new LinkedHashSet<String>();
        List<AgentSessionMemory> memories = memoryService.list(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .eq(AgentSessionMemory::getStatus, AgentSessionMemory.STATUS_ACTIVE)
                .isNotNull(AgentSessionMemory::getCandidateHash));
        if (memories != null) {
            for (AgentSessionMemory memory : memories) {
                if (StringUtils.isNotBlank(memory.getCandidateHash())) {
                    result.add(memory.getCandidateHash());
                }
            }
        }
        return result;
    }

    /**
     * 标准化候选语句。
     */
    private String normalizeStatement(String value) {
        return StringUtils.normalizeSpace(StringUtils.trimToEmpty(value));
    }

    /**
     * 去掉用户显式记忆指令前缀。
     */
    private String stripLeadIn(String value) {
        return StringUtils.trimToEmpty(value)
                .replaceFirst("^(请)?(记住|以后记住|需要记住)[:：，,\\s]*", "");
    }

    /**
     * 检测敏感内容。
     */
    private boolean containsForbiddenContent(String content) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(content));
        for (String marker : FORBIDDEN_CONTENT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 包含任一片段。
     */
    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断自动提取类型。
     */
    private boolean isAutoExtractableType(String type) {
        return "GOAL".equals(type) || "CONSTRAINT".equals(type) || "FACT".equals(type)
                || "DECISION".equals(type) || "TODO".equals(type) || "ARTIFACT".equals(type);
    }

    /**
     * 判断来源消息是否覆盖当前用户消息。
     */
    private boolean containsSourceMessage(JSONArray sourceEventIds, String sourceMessageId) {
        if (sourceEventIds == null || StringUtils.isBlank(sourceMessageId)) {
            return false;
        }
        for (int i = 0; i < sourceEventIds.size(); i++) {
            if (sourceMessageId.equals(sourceEventIds.getString(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成来源范围。
     */
    private String sourceEventRange(JSONArray sourceEventIds) {
        List<String> ids = new ArrayList<String>();
        if (sourceEventIds != null) {
            for (int i = 0; i < sourceEventIds.size() && ids.size() < 8; i++) {
                String id = StringUtils.trimToEmpty(sourceEventIds.getString(i));
                if (StringUtils.isNotBlank(id)) {
                    ids.add(StringUtils.abbreviate(id, 32));
                }
            }
        }
        return StringUtils.join(ids, ",");
    }

    /**
     * 去掉模型可能返回的 Markdown JSON 围栏。
     */
    private String unwrapJsonCodeFence(String response) {
        String json = StringUtils.trimToEmpty(response);
        if (!json.startsWith("```")) {
            return json;
        }
        int firstLineEnd = json.indexOf('\n');
        int closingFence = json.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            return json;
        }
        return json.substring(firstLineEnd + 1, closingFence).trim();
    }

    /**
     * 计算候选哈希。
     */
    private String candidateHash(String sourceMessageId, String type, String content) {
        String value = StringUtils.defaultString(sourceMessageId) + ":"
                + fingerprint(type, content);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * 计算去重指纹。
     */
    private String fingerprint(String type, String content) {
        return StringUtils.upperCase(StringUtils.defaultString(type)) + ":"
                + StringUtils.lowerCase(StringUtils.normalizeSpace(StringUtils.defaultString(content)));
    }

    /**
     * 候选记忆。
     */
    private static class Candidate {
        private final String type;
        private final String content;
        private final Integer confidence;
        private final String sensitivityLevel;
        private final String sourceEventRange;
        private final String candidateHash;

        private Candidate(String type, String content, Integer confidence) {
            this(type, content, confidence, "NORMAL", null, null);
        }

        private Candidate(String type, String content, Integer confidence,
                          String sensitivityLevel, String sourceEventRange, String candidateHash) {
            this.type = type;
            this.content = content;
            this.confidence = confidence;
            this.sensitivityLevel = sensitivityLevel;
            this.sourceEventRange = sourceEventRange;
            this.candidateHash = candidateHash;
        }

        private Candidate withSource(String sourceEventRange, String candidateHash) {
            return new Candidate(type, content, confidence, "NORMAL", sourceEventRange, candidateHash);
        }
    }
}
