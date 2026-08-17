package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.AgentMessageMapper;
import com.aether.agent.model.*;
import com.aether.agent.service.AdminPreferenceExtractionService;
import com.aether.agent.service.AgentMessageContentResolver;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.impl.PreferenceReasoningEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实现管理员偏好Extraction业务服务。
 */
@Service
public class AdminPreferenceExtractionServiceImpl implements AdminPreferenceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AdminPreferenceExtractionServiceImpl.class);

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.valueOf(0.60);
    private static final BigDecimal DEFAULT_CONFIDENCE = BigDecimal.valueOf(0.80);
    private static final int MAX_CONTENT_LENGTH = 512;
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final Set<String> ALLOWED_CATEGORIES = new HashSet<String>();
    private final Set<String> activeExtractions = ConcurrentHashMap.newKeySet();

    static {
        ALLOWED_CATEGORIES.add(AdminPreference.CATEGORY_LANGUAGE);
        ALLOWED_CATEGORIES.add(AdminPreference.CATEGORY_STYLE);
        ALLOWED_CATEGORIES.add(AdminPreference.CATEGORY_FORMAT);
        ALLOWED_CATEGORIES.add(AdminPreference.CATEGORY_TECH_STACK);
        ALLOWED_CATEGORIES.add(AdminPreference.CATEGORY_TOOL_STRATEGY);
    }

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AdminPreferenceEventService eventService;

    @Autowired
    private ModelClientFactory modelClientFactory;

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    /**
     * 处理extractAsync。
     */
    @Override
    @Async("asyncPoolTaskExecutor")
    public void extractAsync(String userId, String conversationId,
                             AgentMessage userMessage, AgentMessage assistantMessage,
                             AgentDefinition agent, ModelProvider provider) {
        String extractionKey = userId + ":" + conversationId;
        if (!activeExtractions.add(extractionKey)) {
            return;
        }
        try {
            doExtract(userId, conversationId, userMessage, assistantMessage, provider, agent);
        } catch (Exception e) {
            log.error("Failed to extract preferences for user {}", userId, e);
        } finally {
            activeExtractions.remove(extractionKey);
        }
    }

    /**
     * 处理doExtract。
     */
    private void doExtract(String userId, String conversationId,
                           AgentMessage userMessage, AgentMessage assistantMessage,
                           ModelProvider provider, AgentDefinition agent) {
        AdminPreferenceEvent marker = eventService.getLastEvent(
                userId, AdminPreferenceEvent.EVENT_EXTRACTION_MARKER, conversationId);
        List<AgentMessage> history = queryConversationHistory(conversationId, marker);
        if (history.size() < 2) {
            return;
        }
        if (!looksLikePreferenceSignal(history)) {
            recordExtractionMarker(userId, conversationId, history.get(history.size() - 1));
            return;
        }

        String response = callCombinedAnalysis(history,
                findLastMessage(history, "user", userMessage),
                findLastMessage(history, "assistant", assistantMessage),
                provider, agent);
        if (StringUtils.isBlank(response)) {
            return;
        }

        List<AdminPreference> extracted = parsePreferences(response);
        for (AdminPreference pref : extracted) {
            savePreference(userId, conversationId, pref);
        }
        recordExtractionMarker(userId, conversationId, history.get(history.size() - 1));
    }

    /**
     * 处理callCombinedAnalysis。
     */
    private String callCombinedAnalysis(List<AgentMessage> messages,
                                        AgentMessage userMessage, AgentMessage assistantMessage,
                                        ModelProvider provider, AgentDefinition agent) {
        StringBuilder raw = new StringBuilder();
        for (AgentMessage msg : messages) {
            String role = "user".equals(msg.getRole()) ? "User" : "Assistant";
            String content = AgentMessageContentResolver.getEffectiveContent(msg);
            if (content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            raw.append(role).append(": ").append(content).append("\n");
        }
        AgentMessage lastUser = userMessage != null ? userMessage
                : findLastMessage(messages, "user", null);
        AgentMessage lastAssistant = assistantMessage != null ? assistantMessage
                : findLastMessage(messages, "assistant", null);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Analyze the following conversation. Return a JSON object with two fields:\n");
        promptBuilder.append("1. \"summary\": a 2-3 sentence summary of what happened in this conversation\n");
        promptBuilder.append("2. \"preferences\": an array of stable, long-term user preferences\n\n");
        promptBuilder.append("=== Full Conversation History ===\n");
        promptBuilder.append(raw.length() > 200 ? raw.substring(0, Math.min(raw.length(), 6000)) : raw);
        promptBuilder.append("\n=== End of History ===\n\n");
        if (lastUser != null) {
            promptBuilder.append("=== Latest User Message ===\n");
            promptBuilder.append(AgentMessageContentResolver.getEffectiveContent(lastUser)).append("\n");
        }
        if (lastAssistant != null) {
            promptBuilder.append("=== Latest Assistant Message ===\n");
            promptBuilder.append(StringUtils.defaultString(lastAssistant.getContent(), "")).append("\n");
        }
        promptBuilder.append("=== End ===\n\n");
        promptBuilder.append("Return only a JSON object in this format (no Markdown code fence): ");
        promptBuilder.append("{\"summary\":\"...\",\"preferences\":[{\"category\":\"language|style|format|tech_stack|tool_strategy\",\"key_name\":\"...\",\"value\":\"...\",\"confidence\":0.0-1.0}]}\n");
        promptBuilder.append("Rules:\n");
        promptBuilder.append("- Only extract RECURRING patterns, not one-time requests\n");
        promptBuilder.append("- Exclude: passwords, tokens, temporary questions, one-off tasks\n");
        promptBuilder.append("- Confidence reflects how certain you are this is a stable preference (0.6-1.0)\n");
        promptBuilder.append("- category must be one of: language, style, format, tech_stack, tool_strategy\n");
        promptBuilder.append("- If the conversation shows a CHANGE in preference, extract the NEW preference\n");

        return callModel(promptBuilder.toString(), provider, agent);
    }

    /**
     * 查询会话历史记录。
     */
    private List<AgentMessage> queryConversationHistory(String conversationId,
                                                        AdminPreferenceEvent marker) {
        if (StringUtils.isBlank(conversationId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AgentMessage> query = new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getMessageType, "chat")
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant");
        AgentMessage coveredMessage = marker == null || StringUtils.isBlank(marker.getMessageId())
                ? null : messageMapper.selectById(marker.getMessageId());
        if (coveredMessage != null && coveredMessage.getCreatedAt() != null) {
            query.and(wrapper -> wrapper
                    .gt(AgentMessage::getCreatedAt, coveredMessage.getCreatedAt())
                    .or(nested -> nested
                            .eq(AgentMessage::getCreatedAt, coveredMessage.getCreatedAt())
                            .gt(AgentMessage::getId, coveredMessage.getId())));
        }
        return messageMapper.selectList(query
                .orderByAsc(AgentMessage::getCreatedAt)
                .orderByAsc(AgentMessage::getId)
                .last("LIMIT " + MAX_CONTEXT_MESSAGES));
    }

    /**
     * 处理looksLike偏好Signal。
     */
    private boolean looksLikePreferenceSignal(List<AgentMessage> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (AgentMessage msg : history) {
            if ("user".equals(msg.getRole())
                    && containsPreferenceSignal(AgentMessageContentResolver.getEffectiveContent(msg))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理contains偏好Signal。
     */
    private boolean containsPreferenceSignal(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String lower = content.toLowerCase();
        String[] signals = {
                "用中文", "用英文", "用英语", "in chinese", "in english",
                "简洁", "详细", "简短", "brief", "detailed", "concise",
                "不要", "别用", "禁止", "don't", "avoid", "never use",
                "总是", "每次", "always", "every time",
                "prefer", "偏好", "喜欢", "习惯",
                "typescript", "javascript", "python", "java",
                "注释", "comment", "format", "格式"
        };
        for (String signal : signals) {
            if (lower.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析Preferences。
     */
    private List<AdminPreference> parsePreferences(String response) {
        List<AdminPreference> result = new ArrayList<>();
        String json = unwrapJsonCodeFence(response);
        if (StringUtils.isBlank(json)) {
            return result;
        }

        try {
            String trimmed = json.trim();
            JSONArray arr;
            if (trimmed.startsWith("{")) {
                JSONObject payload = JSONObject.parseObject(trimmed);
                arr = payload == null ? null : payload.getJSONArray("preferences");
            } else {
                // Keep accepting the legacy array-only response while the prompt is rolled out.
                arr = JSONArray.parseArray(trimmed);
            }
            if (arr == null) {
                log.warn("Ignoring preference extraction response without a preferences array");
                return result;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String category = obj.getString("category");
                String keyName = obj.getString("key_name");
                String value = obj.getString("value");
                BigDecimal confidence = obj.getBigDecimal("confidence");
                if (category == null) category = "";
                if (keyName == null) keyName = "";
                if (value == null) value = "";
                if (confidence == null) confidence = DEFAULT_CONFIDENCE;
                category = category.trim().toLowerCase();
                keyName = keyName.trim().toLowerCase();
                value = value.trim();

                if (!ALLOWED_CATEGORIES.contains(category)
                        || !keyName.matches("[a-z0-9_.-]{1,128}")
                        || StringUtils.isBlank(value)
                        || value.length() > MAX_CONTENT_LENGTH) {
                    continue;
                }
                if (confidence.compareTo(MIN_CONFIDENCE) < 0
                        || confidence.compareTo(BigDecimal.ONE) > 0) {
                    continue;
                }

                AdminPreference pref = new AdminPreference();
                pref.setCategory(category);
                pref.setKeyName(keyName);
                pref.setValue(value);
                pref.setConfidence(confidence);
                result.add(pref);
            }
        } catch (Exception e) {
            // Model output is untrusted; ignore a malformed response without failing the chat flow.
            log.warn("Ignoring malformed preference extraction response: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 处理unwrapJsonCodeFence。
     */
    private String unwrapJsonCodeFence(String response) {
        if (StringUtils.isBlank(response)) {
            return response;
        }
        String json = response.trim();
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
     * 处理call模型。
     */
    private String callModel(String prompt, ModelProvider provider, AgentDefinition agent) {
        try {
            ModelChatMessage msg = new ModelChatMessage("user", prompt);
            ModelChatRequest request = new ModelChatRequest();
            request.setProvider(provider);
            request.setMessages(Collections.singletonList(msg));
            request.setAgent(agent);
            ModelClient client = modelClientFactory.getClient(provider);
            ModelChatResponse response = client.chat(request);
            return response.getContent();
        } catch (Exception e) {
            log.error("Failed to call model for preference extraction", e);
            return null;
        }
    }

    /**
     * 保存偏好。
     */
    private void savePreference(String userId, String conversationId, AdminPreference extracted) {
        String scope = AdminPreference.SCOPE_GLOBAL;
        String scopeDetail = "";
        AdminPreference existing = preferenceMapper.selectByIdentity(
                userId, extracted.getCategory(), extracted.getKeyName(), scope, scopeDetail);
        if (existing != null) {
            if (existing.getValue().equals(extracted.getValue())) {
                existing.setUsageCount((existing.getUsageCount() != null ? existing.getUsageCount() : 0) + 1);
                existing.setLastUsedAt(System.currentTimeMillis());
                BigDecimal newConfidence = existing.getConfidence().add(new BigDecimal("0.03"));
                if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                    newConfidence = BigDecimal.ONE;
                }
                existing.setConfidence(newConfidence);
                preferenceMapper.updateById(existing);
                AdminPreferenceEvent reinforcementEvent = new AdminPreferenceEvent();
                reinforcementEvent.setAdminId(userId);
                reinforcementEvent.setPreferenceId(existing.getId());
                reinforcementEvent.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
                reinforcementEvent.setCategory(existing.getCategory());
                reinforcementEvent.setKeyName(existing.getKeyName());
                reinforcementEvent.setValue(extracted.getValue());
                reinforcementEvent.setConfidence(extracted.getConfidence());
                reinforcementEvent.setConversationId(conversationId);
                reinforcementEvent.setContextSnapshot("reinforcement");
                eventService.logEvent(reinforcementEvent);
                reasoningEngine.clearUserCache(userId);
                return;
            }

            if (isConflict(existing, extracted)) {
                existing.setValue(extracted.getValue());
                existing.setConfidence(extracted.getConfidence());
                existing.setLastUsedAt(System.currentTimeMillis());
                preferenceMapper.updateById(existing);
                AdminPreferenceEvent conflictEvent = new AdminPreferenceEvent();
                conflictEvent.setAdminId(userId);
                conflictEvent.setPreferenceId(existing.getId());
                conflictEvent.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
                conflictEvent.setCategory(existing.getCategory());
                conflictEvent.setKeyName(existing.getKeyName());
                conflictEvent.setValue(extracted.getValue());
                conflictEvent.setConfidence(extracted.getConfidence());
                conflictEvent.setConversationId(conversationId);
                conflictEvent.setContextSnapshot("conflict_update");
                eventService.logEvent(conflictEvent);
                reasoningEngine.clearUserCache(userId);
                return;
            }
        }

        AdminPreference pref = new AdminPreference();
        pref.setAdminId(userId);
        pref.setCategory(extracted.getCategory());
        pref.setKeyName(extracted.getKeyName());
        pref.setValue(extracted.getValue());
        pref.setDescription(extracted.getValue());
        pref.setPriority(50);
        pref.setScope(AdminPreference.SCOPE_GLOBAL);
        pref.setScopeDetail("");
        pref.setSource(AdminPreference.SOURCE_IMPLICIT);
        pref.setConfidence(extracted.getConfidence());
        pref.setUsageCount(0);
        pref.setDecayRate(defaultDecayRate(extracted.getCategory()));
        pref.setEffectiveScore(BigDecimal.valueOf(50));
        pref.setStatus(AdminPreference.STATUS_ENABLED);
        preferenceMapper.insert(pref);
        reasoningEngine.clearUserCache(userId);

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(userId);
        event.setPreferenceId(pref.getId());
        event.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
        event.setCategory(extracted.getCategory());
        event.setKeyName(extracted.getKeyName());
        event.setValue(extracted.getValue());
        event.setConfidence(extracted.getConfidence());
        event.setConversationId(conversationId);
        eventService.logEvent(event);
    }

    /**
     * 判断是否为Conflict。
     */
    private boolean isConflict(AdminPreference existing, AdminPreference extracted) {
        if (!existing.getCategory().equals(extracted.getCategory())) {
            return false;
        }
        String key = existing.getKeyName().toLowerCase();
        if (key.contains("length") || key.contains("detail") || key.contains("brief")
                || key.contains("concise") || key.contains("verbose")) {
            return true;
        }
        if (key.contains("language") || key.contains("lang")) {
            return true;
        }
        return false;
    }

    /**
     * 处理recordExtractionMarker。
     */
    private void recordExtractionMarker(String userId, String conversationId,
                                        AgentMessage coveredMessage) {
        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(userId);
        event.setEventType(AdminPreferenceEvent.EVENT_EXTRACTION_MARKER);
        event.setConversationId(conversationId);
        event.setMessageId(coveredMessage.getId());
        event.setContextSnapshot("processed_through_message");
        eventService.logEvent(event);
    }

    /**
     * 查找Last消息。
     */
    private AgentMessage findLastMessage(List<AgentMessage> messages, String role,
                                         AgentMessage fallback) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (role.equals(messages.get(i).getRole())) {
                return messages.get(i);
            }
        }
        return fallback;
    }

    /**
     * 处理defaultDecayRate。
     */
    private BigDecimal defaultDecayRate(String category) {
        if (category == null) {
            return BigDecimal.ZERO;
        }
        switch (category) {
            case AdminPreference.CATEGORY_LANGUAGE:
                return BigDecimal.ZERO;
            case AdminPreference.CATEGORY_STYLE:
                return new BigDecimal("0.005");
            case AdminPreference.CATEGORY_FORMAT:
                return new BigDecimal("0.01");
            case AdminPreference.CATEGORY_TECH_STACK:
                return new BigDecimal("0.02");
            case AdminPreference.CATEGORY_TOOL_STRATEGY:
                return new BigDecimal("0.01");
            default:
                return BigDecimal.ZERO;
        }
    }
}
