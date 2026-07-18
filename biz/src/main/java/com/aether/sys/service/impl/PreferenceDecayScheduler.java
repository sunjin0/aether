package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PreferenceDecayScheduler {

    private static final long MILLIS_PER_DAY = 24 * 3600 * 1000L;

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    @Scheduled(cron = "0 0 2 * * ?")
    public void recalculateEffectiveScores() {
        List<AdminPreference> allPreferences = preferenceMapper.selectList(
                Wrappers.lambdaQuery(AdminPreference.class)
                        .eq(AdminPreference::getStatus, AdminPreference.STATUS_ENABLED)
                        .eq(AdminPreference::getDeleted, false));

        long now = System.currentTimeMillis();

        for (AdminPreference pref : allPreferences) {
            BigDecimal newScore = calculateEffectiveScore(pref, now);
            pref.setEffectiveScore(newScore);
            preferenceMapper.updateEffectiveScore(pref.getId(), newScore);
        }

        reasoningEngine.clearUserCache(null);
    }

    private BigDecimal calculateEffectiveScore(AdminPreference pref, long now) {
        BigDecimal priority = BigDecimal.valueOf(pref.getPriority() != null ? pref.getPriority() : 50);
        BigDecimal confidence = pref.getConfidence() != null ? pref.getConfidence() : BigDecimal.ONE;
        BigDecimal decayFactor = calculateDecayFactor(pref, now);
        return priority.multiply(decayFactor).multiply(confidence).setScale(2, RoundingMode.HALF_UP);
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
}