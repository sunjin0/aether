package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class PreferenceReasoningEngine {

    private static final String CACHE_PREFIX = "pref:ctx:";
    private static final long CACHE_TTL_MINUTES = 5;
    private static final int MAX_PROMPT_LENGTH = 2000;
    private static final long MILLIS_PER_DAY = 24 * 3600 * 1000L;
    private static final BigDecimal MIN_EFFECTIVE_SCORE = BigDecimal.valueOf(10);

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public List<AdminPreference> resolveEffectivePreferences(String adminId, String taskType) {
        List<AdminPreference> allPreferences = preferenceMapper.selectEffectivePreferences(adminId);
        if (allPreferences == null || allPreferences.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();

        List<AdminPreference> effective = allPreferences.stream()
                .filter(p -> !isExpired(p, now))
                .filter(p -> matchesScope(p, taskType))
                .collect(Collectors.toList());

        Map<String, AdminPreference> bestByKey = new LinkedHashMap<>();
        for (AdminPreference pref : effective) {
            BigDecimal score = calculateEffectiveScore(pref, now);
            pref.setEffectiveScore(score);

            if (score.compareTo(MIN_EFFECTIVE_SCORE) < 0) {
                continue;
            }

            String key = pref.getKeyName();
            AdminPreference existing = bestByKey.get(key);
            if (existing == null) {
                bestByKey.put(key, pref);
            } else {
                int scopeCmp = Integer.compare(scopePriority(pref.getScope()), scopePriority(existing.getScope()));
                if (scopeCmp > 0 || (scopeCmp == 0 && score.compareTo(existing.getEffectiveScore()) > 0)) {
                    bestByKey.put(key, pref);
                }
            }
        }

        return bestByKey.values().stream()
                .sorted(Comparator.comparing(AdminPreference::getEffectiveScore).reversed())
                .collect(Collectors.toList());
    }

    public String buildPreferenceContext(String adminId, String taskType) {
        String cacheKey = CACHE_PREFIX + adminId + ":" + (taskType != null ? taskType : "default");
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        List<AdminPreference> effective = resolveEffectivePreferences(adminId, taskType);
        if (effective.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, "", CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }

        long now = System.currentTimeMillis();
        for (AdminPreference pref : effective) {
            pref.setUsageCount((pref.getUsageCount() != null ? pref.getUsageCount() : 0) + 1);
            pref.setLastUsedAt(now);
            BigDecimal newConfidence = (pref.getConfidence() != null ? pref.getConfidence() : BigDecimal.ONE)
                    .add(new BigDecimal("0.05"));
            if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                newConfidence = BigDecimal.ONE;
            }
            pref.setConfidence(newConfidence);
            pref.setEffectiveScore(calculateEffectiveScore(pref, now));
            preferenceMapper.updateById(pref);
        }

        StringBuilder builder = new StringBuilder("【User Preferences (sorted by priority)】\n");
        for (AdminPreference pref : effective) {
            if (StringUtils.isBlank(pref.getValue())) {
                continue;
            }
            builder.append("- [").append(pref.getCategory()).append("] ");
            builder.append(pref.getValue());
            if (StringUtils.isNotBlank(pref.getScopeDetail())) {
                builder.append(" (scope: ").append(pref.getScope()).append(":").append(pref.getScopeDetail()).append(")");
            } else {
                builder.append(" (scope: ").append(pref.getScope()).append(")");
            }
            builder.append(", priority: ").append(pref.getEffectiveScore().intValue());
            builder.append('\n');

            if (builder.length() > MAX_PROMPT_LENGTH) {
                break;
            }
        }
        String context = builder.toString();
        redisTemplate.opsForValue().set(cacheKey, context, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return context;
    }

    private boolean isExpired(AdminPreference pref, long now) {
        return pref.getExpiresAt() != null && pref.getExpiresAt() < now;
    }

    private boolean matchesScope(AdminPreference pref, String taskType) {
        if (AdminPreference.SCOPE_GLOBAL.equals(pref.getScope())) {
            return true;
        }
        if (AdminPreference.SCOPE_SESSION.equals(pref.getScope())) {
            return true;
        }
        if (AdminPreference.SCOPE_TASK_TYPE.equals(pref.getScope())) {
            return StringUtils.isNotBlank(taskType) && taskType.equals(pref.getScopeDetail());
        }
        return false;
    }

    private int scopePriority(String scope) {
        if (AdminPreference.SCOPE_TASK_TYPE.equals(scope)) {
            return 3;
        }
        if (AdminPreference.SCOPE_SESSION.equals(scope)) {
            return 2;
        }
        return 1;
    }

    private BigDecimal calculateEffectiveScore(AdminPreference pref, long now) {
        BigDecimal priority = BigDecimal.valueOf(pref.getPriority() != null ? pref.getPriority() : 50);
        BigDecimal confidence = pref.getConfidence() != null ? pref.getConfidence() : BigDecimal.ONE;
        BigDecimal decayFactor = calculateDecayFactor(pref, now);
        int usageCount = pref.getUsageCount() != null ? pref.getUsageCount() : 0;
        BigDecimal usageBoost = BigDecimal.valueOf(Math.log(1 + usageCount));
        return priority.multiply(decayFactor).multiply(confidence).add(usageBoost).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDecayFactor(AdminPreference pref, long now) {
        if (pref.getDecayRate() == null || pref.getDecayRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        if (pref.getLastUsedAt() == null) {
            return BigDecimal.ONE;
        }
        long daysSinceLastUse = (now - pref.getLastUsedAt()) / MILLIS_PER_DAY;
        double factor = Math.max(0.1, 1.0 - pref.getDecayRate().doubleValue() * daysSinceLastUse);
        return BigDecimal.valueOf(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public void clearUserCache(String adminId) {
        if (adminId == null) {
            Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } else {
            Set<String> keys = redisTemplate.keys(CACHE_PREFIX + adminId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

}
