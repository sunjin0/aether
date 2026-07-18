package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.mapper.AgentConversationMapper;
import com.aether.agent.mapper.AgentMessageMapper;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.model.*;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 跨对话模式分析服务。
 * 定期扫描近期对话，识别跨多轮对话的用户偏好模式。
 */
@Service
public class CrossConversationPatternService {

    private static final Logger log = LoggerFactory.getLogger(CrossConversationPatternService.class);

    private static final int RECENT_CONVERSATIONS = 50;
    private static final int MAX_MESSAGES_PER_CONVERSATION = 10;
    private static final BigDecimal MIN_PATTERN_CONFIDENCE = BigDecimal.valueOf(0.65);
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
    private ModelClientFactory modelClientFactory;

    @Scheduled(cron = "0 0 3 * * ?")
    public void analyzeRecentPatterns() {
        log.info("Starting cross-conversation pattern analysis");
        long cutoff = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L;

        List<AgentConversation> recentConversations = conversationMapper.selectList(
                new LambdaQueryWrapper<AgentConversation>()
                        .ge(AgentConversation::getCreatedAt, cutoff)
                        .orderByDesc(AgentConversation::getCreatedAt)
                        .last("LIMIT " + RECENT_CONVERSATIONS));

        if (recentConversations.isEmpty()) {
            return;
        }

        Map<String, List<String>> userLanguagePatterns = new HashMap<>();
        Map<String, List<String>> userToolPatterns = new HashMap<>();

        for (AgentConversation conv : recentConversations) {
            List<AgentMessage> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<AgentMessage>()
                            .eq(AgentMessage::getConversationId, conv.getId())
                            .eq(AgentMessage::getMessageType, "chat")
                            .orderByAsc(AgentMessage::getCreatedAt)
                            .last("LIMIT " + MAX_MESSAGES_PER_CONVERSATION));

            String userId = conv.getUserId();
            for (AgentMessage msg : messages) {
                if (!"user".equals(msg.getRole()) || StringUtils.isBlank(msg.getContent())) {
                    continue;
                }
                String content = msg.getContent().toLowerCase();

                if (containsLanguageSignal(content)) {
                    userLanguagePatterns.computeIfAbsent(userId, k -> new ArrayList<>()).add(content);
                }
                if (containsToolSignal(content)) {
                    userToolPatterns.computeIfAbsent(userId, k -> new ArrayList<>()).add(content);
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : userLanguagePatterns.entrySet()) {
            detectAndSavePattern(entry.getKey(), "language", entry.getValue(),
                    "preferred_language", "User consistently uses language preference signals");
        }
        for (Map.Entry<String, List<String>> entry : userToolPatterns.entrySet()) {
            detectAndSavePattern(entry.getKey(), "tool_strategy", entry.getValue(),
                    "tool_usage_pattern", "User shows consistent tool usage patterns");
        }

        log.info("Cross-conversation pattern analysis completed");
    }

    private void detectAndSavePattern(String userId, String category,
                                       List<String> signals, String keyName, String description) {
        if (signals.size() < 3) {
            return;
        }

        AdminPreference existing = preferenceMapper.selectOne(
                new LambdaQueryWrapper<AdminPreference>()
                        .eq(AdminPreference::getAdminId, userId)
                        .eq(AdminPreference::getKeyName, keyName)
                        .eq(AdminPreference::getDeleted, false));

        if (existing != null) {
            existing.setUsageCount(existing.getUsageCount() + signals.size());
            existing.setLastUsedAt(System.currentTimeMillis());
            BigDecimal newConfidence = existing.getConfidence()
                    .add(new BigDecimal("0.05").multiply(BigDecimal.valueOf(signals.size() / 3)));
            if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                newConfidence = BigDecimal.ONE;
            }
            existing.setConfidence(newConfidence);
            preferenceMapper.updateById(existing);
            return;
        }

        String dominantPattern = findDominantPattern(signals);
        if (StringUtils.isBlank(dominantPattern)) {
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
        pref.setSource(AdminPreference.SOURCE_IMPLICIT);
        pref.setConfidence(PATTERN_CONFIDENCE);
        pref.setUsageCount(signals.size());
        pref.setLastUsedAt(System.currentTimeMillis());
        pref.setDecayRate(new BigDecimal("0.01"));
        pref.setEffectiveScore(BigDecimal.valueOf(50));
        pref.setStatus(AdminPreference.STATUS_ENABLED);
        preferenceMapper.insert(pref);

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(userId);
        event.setPreferenceId(pref.getId());
        event.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
        event.setCategory(category);
        event.setKeyName(keyName);
        event.setValue(dominantPattern);
        event.setConfidence(PATTERN_CONFIDENCE);
        eventService.logEvent(event);
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

    private boolean containsLanguageSignal(String content) {
        String[] keywords = {"用中文", "用英文", "用英语", "in chinese", "in english",
                "中文回答", "英文回答", "chinese", "english"};
        for (String kw : keywords) {
            if (content.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsToolSignal(String content) {
        String[] keywords = {"shell", "terminal", "命令行", "maven", "gradle",
                "npm", "yarn", "pip", "工具", "tool"};
        for (String kw : keywords) {
            if (content.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
