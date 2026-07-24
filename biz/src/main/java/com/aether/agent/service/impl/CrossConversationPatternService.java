package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.mapper.AgentConversationMapper;
import com.aether.agent.mapper.AgentMessageMapper;
import com.aether.agent.entity.AgentConversation;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 跨对话模式分析服务。
 * 定期扫描近期对话，识别跨多轮对话的用户偏好模式。
 */
@Service
public class CrossConversationPatternService {

    private static final Logger log = LoggerFactory.getLogger(CrossConversationPatternService.class);

    private static final int RECENT_CONVERSATIONS = 50;
    private static final int MAX_MESSAGES_PER_CONVERSATION = 10;
    private static final BigDecimal PATTERN_CONFIDENCE = BigDecimal.valueOf(0.75);

    @Autowired
    private AgentConversationMapper conversationMapper;

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private AdminPreferenceEventService eventService;

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    @Scheduled(cron = "0 0 3 * * ?")
    public void analyzeRecentPatterns() {
        log.info("Starting cross-conversation pattern analysis");
        long cutoff = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L;

        List<AgentConversation> recentConversations = conversationMapper.selectList(
                new LambdaQueryWrapper<AgentConversation>()
                        .ge(AgentConversation::getCreatedAt, cutoff)
                        .eq(AgentConversation::getDeleted, false)
                        .orderByDesc(AgentConversation::getCreatedAt)
                        .last("LIMIT " + RECENT_CONVERSATIONS));

        if (recentConversations.isEmpty()) {
            return;
        }

        Map<String, List<PatternSignal>> userLanguagePatterns = new HashMap<>();
        Map<String, List<PatternSignal>> userToolPatterns = new HashMap<>();

        for (AgentConversation conv : recentConversations) {
            List<AgentMessage> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<AgentMessage>()
                            .eq(AgentMessage::getConversationId, conv.getId())
                            .eq(AgentMessage::getMessageType, "chat")
                            .eq(AgentMessage::getDeleted, false)
                            .orderByDesc(AgentMessage::getCreatedAt)
                            .orderByDesc(AgentMessage::getId)
                            .last("LIMIT " + MAX_MESSAGES_PER_CONVERSATION));

            String userId = conv.getUserId();
            for (AgentMessage msg : messages) {
                if (!"user".equals(msg.getRole()) || StringUtils.isBlank(msg.getContent())) {
                    continue;
                }
                String content = msg.getContent().toLowerCase();

                String languagePattern = detectLanguagePattern(content);
                if (languagePattern != null) {
                    userLanguagePatterns.computeIfAbsent(userId, k -> new ArrayList<>())
                            .add(new PatternSignal(languagePattern, conv.getId(), msg.getId()));
                }
                String toolPattern = detectToolPattern(content);
                if (toolPattern != null) {
                    userToolPatterns.computeIfAbsent(userId, k -> new ArrayList<>())
                            .add(new PatternSignal(toolPattern, conv.getId(), msg.getId()));
                }
            }
        }

        for (Map.Entry<String, List<PatternSignal>> entry : userLanguagePatterns.entrySet()) {
            detectAndSavePattern(entry.getKey(), "language", entry.getValue(),
                    "preferred_language", "User consistently uses language preference signals");
        }
        for (Map.Entry<String, List<PatternSignal>> entry : userToolPatterns.entrySet()) {
            detectAndSavePattern(entry.getKey(), "tool_strategy", entry.getValue(),
                    "tool_usage_pattern", "User shows consistent tool usage patterns");
        }

        log.info("Cross-conversation pattern analysis completed");
    }

    private void detectAndSavePattern(String userId, String category,
                                       List<PatternSignal> signals, String keyName, String description) {
        if (signals.size() < 3) {
            return;
        }

        String dominantPattern = findDominantPattern(signals.stream()
                .map(PatternSignal::getValue).collect(java.util.stream.Collectors.toList()));
        if (StringUtils.isBlank(dominantPattern)) {
            return;
        }
        List<PatternSignal> supportingSignals = signals.stream()
                .filter(signal -> dominantPattern.equals(
                        signal.getValue().trim().toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
        long supportingConversations = supportingSignals.stream()
                .map(PatternSignal::getConversationId).distinct().count();
        if (supportingConversations < 3) {
            return;
        }

        AdminPreference existing = preferenceMapper.selectByIdentity(
                userId, category, keyName, AdminPreference.SCOPE_GLOBAL, "");

        if (existing != null) {
            if (!dominantPattern.equals(existing.getValue())) {
                existing.setValue(dominantPattern);
                existing.setLastUsedAt(System.currentTimeMillis());
                existing.setConfidence(PATTERN_CONFIDENCE);
                preferenceMapper.updateById(existing);
                reasoningEngine.clearUserCache(userId);
            }
            recordPatternEvidence(existing, supportingSignals, dominantPattern);
            return;
        }

        AdminPreference pref = new AdminPreference();
        pref.setAdminId(userId);
        pref.setCategory(category);
        pref.setKeyName(keyName);
        pref.setValue(dominantPattern);
        pref.setDescription(description);
        pref.setPriority(50);
        pref.setScope(AdminPreference.SCOPE_GLOBAL);
        pref.setScopeDetail("");
        pref.setSource(AdminPreference.SOURCE_IMPLICIT);
        pref.setConfidence(PATTERN_CONFIDENCE);
        pref.setUsageCount(signals.size());
        pref.setLastUsedAt(System.currentTimeMillis());
        pref.setDecayRate(new BigDecimal("0.01"));
        pref.setEffectiveScore(BigDecimal.valueOf(50));
        pref.setStatus(AdminPreference.STATUS_ENABLED);
        preferenceMapper.insert(pref);
        reasoningEngine.clearUserCache(userId);
        recordPatternEvidence(pref, supportingSignals, dominantPattern);
    }

    private void recordPatternEvidence(AdminPreference preference,
                                       List<PatternSignal> signals,
                                       String value) {
        Map<String, PatternSignal> byConversation = new LinkedHashMap<String, PatternSignal>();
        for (PatternSignal signal : signals) {
            byConversation.putIfAbsent(signal.getConversationId(), signal);
        }
        for (PatternSignal signal : byConversation.values()) {
            long existingEvidence = eventService.count(
                    new LambdaQueryWrapper<AdminPreferenceEvent>()
                            .eq(AdminPreferenceEvent::getPreferenceId, preference.getId())
                            .eq(AdminPreferenceEvent::getEventType,
                                    AdminPreferenceEvent.EVENT_EXTRACT)
                            .eq(AdminPreferenceEvent::getConversationId,
                                    signal.getConversationId())
                            .eq(AdminPreferenceEvent::getValue, value)
                            .eq(AdminPreferenceEvent::getDeleted, false));
            if (existingEvidence > 0L) {
                continue;
            }
            AdminPreferenceEvent event = new AdminPreferenceEvent();
            event.setAdminId(preference.getAdminId());
            event.setPreferenceId(preference.getId());
            event.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
            event.setCategory(preference.getCategory());
            event.setKeyName(preference.getKeyName());
            event.setValue(value);
            event.setConfidence(PATTERN_CONFIDENCE);
            event.setConversationId(signal.getConversationId());
            event.setMessageId(signal.getMessageId());
            event.setContextSnapshot("cross_conversation_pattern");
            eventService.logEvent(event);
        }
    }

    private String findDominantPattern(List<String> signals) {
        Map<String, Integer> freq = new HashMap<>();
        for (String signal : signals) {
            String normalized = signal.trim().toLowerCase();
            freq.merge(normalized, 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String detectLanguagePattern(String content) {
        String[] chinese = {"用中文", "中文回答", "in chinese", "chinese"};
        for (String keyword : chinese) {
            if (content.contains(keyword)) {
                return "zh-CN";
            }
        }
        String[] english = {"用英文", "用英语", "英文回答", "in english", "english"};
        for (String keyword : english) {
            if (content.contains(keyword)) {
                return "en";
            }
        }
        return null;
    }

    private String detectToolPattern(String content) {
        String[] commandLine = {"shell", "terminal", "命令行"};
        for (String keyword : commandLine) {
            if (content.contains(keyword)) {
                return "command_line";
            }
        }
        String[] buildTools = {"maven", "gradle", "npm", "yarn", "pip"};
        for (String keyword : buildTools) {
            if (content.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private static class PatternSignal {
        private final String value;
        private final String conversationId;
        private final String messageId;

        PatternSignal(String value, String conversationId, String messageId) {
            this.value = value;
            this.conversationId = conversationId;
            this.messageId = messageId;
        }

        String getValue() {
            return value;
        }

        String getConversationId() {
            return conversationId;
        }

        String getMessageId() {
            return messageId;
        }
    }
}
