package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.*;
import com.aether.agent.service.AdminPreferenceExtractionService;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;

@Service
public class AdminPreferenceExtractionServiceImpl implements AdminPreferenceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AdminPreferenceExtractionServiceImpl.class);

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.valueOf(0.60);
    private static final BigDecimal DEFAULT_CONFIDENCE = BigDecimal.valueOf(0.80);
    private static final BigDecimal CONFIDENCE_REDUCE_ON_DUPLICATE = BigDecimal.valueOf(0.10);
    private static final int MAX_CONTENT_LENGTH = 512;

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private AdminPreferenceEventService eventService;

    @Autowired
    private ModelClientFactory modelClientFactory;

    @Override
    @Async
    public void extractAsync(String userId, String conversationId,
                             AgentMessage userMessage, AgentMessage assistantMessage,
                             AgentDefinition agent, ModelProvider provider) {
        try {
            doExtract(userId, conversationId, userMessage, assistantMessage, provider, agent);
        } catch (Exception e) {
            log.error("Failed to extract preferences for user {}", userId, e);
        }
    }

    private void doExtract(String userId, String conversationId,
                           AgentMessage userMessage, AgentMessage assistantMessage,
                           ModelProvider provider, AgentDefinition agent) {
        String extractionPrompt = buildExtractionPrompt(userMessage, assistantMessage);

        String response = callModel(extractionPrompt, provider, agent);
        if (StringUtils.isBlank(response)) {
            return;
        }

        parseAndSavePreferences(userId, conversationId, response);
    }

    private String buildExtractionPrompt(AgentMessage userMessage, AgentMessage assistantMessage) {
        return "Extract stable, long-term preferences from this conversation.\n" +
                "Return JSON array: [{\"category\":\"language|style|format|tech_stack|tool_strategy\",\"key_name\":\"preference_key\",\"value\":\"preference_value\",\"confidence\":0.0-1.0}]\n" +
                "Exclude: one-time tasks, temporary questions, passwords, tokens.\n\n" +
                "User: " + userMessage.getContent() + "\n" +
                "Assistant: " + assistantMessage.getContent();
    }

    private String callModel(String prompt, ModelProvider provider,AgentDefinition agent) {
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

    private void parseAndSavePreferences(String userId, String conversationId, String response) {
        String json = response;
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
        }

        try {
            org.json.JSONArray arr = new org.json.JSONArray(json.trim());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String category = obj.optString("category", "general");
                String keyName = obj.optString("key_name", "");
                String value = obj.optString("value", "");
                BigDecimal confidence = new BigDecimal(obj.optString("confidence", DEFAULT_CONFIDENCE.toString()));

                if (StringUtils.isBlank(value) || value.length() > MAX_CONTENT_LENGTH) {
                    continue;
                }
                if (confidence.compareTo(MIN_CONFIDENCE) < 0) {
                    continue;
                }

                savePreference(userId, conversationId, category, keyName, value, confidence);
            }
        } catch (Exception e) {
            log.error("Failed to parse extraction response", e);
        }
    }

    private void savePreference(String userId, String conversationId,
                                String category, String keyName, String value, BigDecimal confidence) {
        AdminPreference existing = preferenceMapper.selectByKey(userId, keyName);
        if (existing != null) {
            if (existing.getValue().equals(value)) {
                existing.setUsageCount(existing.getUsageCount() + 1);
                existing.setLastUsedAt(System.currentTimeMillis());
                preferenceMapper.updateById(existing);
                return;
            }
            confidence = confidence.subtract(CONFIDENCE_REDUCE_ON_DUPLICATE);
        }

        AdminPreference pref = new AdminPreference();
        pref.setAdminId(userId);
        pref.setCategory(category);
        pref.setKeyName(keyName);
        pref.setValue(value);
        pref.setDescription(value);
        pref.setPriority(50);
        pref.setScope(AdminPreference.SCOPE_GLOBAL);
        pref.setSource(AdminPreference.SOURCE_IMPLICIT);
        pref.setConfidence(confidence);
        pref.setUsageCount(0);
        pref.setDecayRate(BigDecimal.ZERO);
        pref.setEffectiveScore(BigDecimal.valueOf(50));
        pref.setStatus(AdminPreference.STATUS_ENABLED);
        preferenceMapper.insert(pref);

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(userId);
        event.setPreferenceId(pref.getId());
        event.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
        event.setCategory(category);
        event.setKeyName(keyName);
        event.setValue(value);
        event.setConfidence(confidence);
        event.setConversationId(conversationId);
        eventService.logEvent(event);
    }
}
