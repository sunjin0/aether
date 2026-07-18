package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminPreferenceServiceImpl extends ServiceImpl<AdminPreferenceMapper, AdminPreference>
        implements AdminPreferenceService {

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    @Override
    public String buildPreferenceContext(String adminId, String taskType) {
        return reasoningEngine.buildPreferenceContext(adminId, taskType);
    }

    @Override
    public AdminPreference getEffectivePreference(String adminId, String keyName, String taskType) {
        List<AdminPreference> effective = reasoningEngine.resolveEffectivePreferences(adminId, taskType);
        return effective.stream()
                .filter(p -> p.getKeyName().equals(keyName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void incrementUsage(String preferenceId) {
        AdminPreference pref = getById(preferenceId);
        if (pref != null) {
            pref.setUsageCount(pref.getUsageCount() + 1);
            pref.setLastUsedAt(System.currentTimeMillis());
            BigDecimal newConfidence = pref.getConfidence().add(new BigDecimal("0.05"));
            if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                newConfidence = BigDecimal.ONE;
            }
            pref.setConfidence(newConfidence);
            updateById(pref);
            updateEffectiveScore(preferenceId);
        }
    }

    @Override
    public void adjustConfidence(String preferenceId, BigDecimal delta) {
        AdminPreference pref = getById(preferenceId);
        if (pref != null) {
            BigDecimal newConfidence = pref.getConfidence().add(delta);
            if (newConfidence.compareTo(BigDecimal.ZERO) < 0) {
                newConfidence = BigDecimal.ZERO;
            }
            if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                newConfidence = BigDecimal.ONE;
            }
            pref.setConfidence(newConfidence);

            if (newConfidence.compareTo(BigDecimal.valueOf(0.3)) < 0) {
                pref.setStatus(AdminPreference.STATUS_DISABLED);
            }

            updateById(pref);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    @Override
    public void updateEffectiveScore(String preferenceId) {
        AdminPreference pref = getById(preferenceId);
        if (pref != null) {
            BigDecimal decayFactor = BigDecimal.ONE;
            BigDecimal decayRate = pref.getDecayRate();
            Long lastUsedAt = pref.getLastUsedAt();
            if (decayRate != null && lastUsedAt != null && decayRate.compareTo(BigDecimal.ZERO) != 0) {
                long daysSinceLastUse = (System.currentTimeMillis() - lastUsedAt) / (1000 * 60 * 60 * 24);
                decayFactor = BigDecimal.ONE.subtract(
                        decayRate.multiply(BigDecimal.valueOf(daysSinceLastUse)));
                if (decayFactor.compareTo(BigDecimal.valueOf(0.1)) < 0) {
                    decayFactor = BigDecimal.valueOf(0.1);
                }
            }

            int priority = pref.getPriority() != null ? pref.getPriority() : 50;
            BigDecimal confidence = pref.getConfidence() != null ? pref.getConfidence() : BigDecimal.ONE;
            int usageCount = pref.getUsageCount() != null ? pref.getUsageCount() : 0;
            BigDecimal usageBoost = BigDecimal.valueOf(Math.log(1 + usageCount));
            BigDecimal score = BigDecimal.valueOf(priority)
                    .multiply(decayFactor)
                    .multiply(confidence)
                    .add(usageBoost);

            pref.setEffectiveScore(score);
            updateById(pref);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    @Override
    public List<AdminPreference> listByAdminId(String adminId) {
        return list(Wrappers.lambdaQuery(AdminPreference.class)
                .eq(AdminPreference::getAdminId, adminId)
                .eq(AdminPreference::getDeleted, false)
                .orderByDesc(AdminPreference::getEffectiveScore));
    }

    @Override
    public boolean clearUserCache(String adminId) {
        reasoningEngine.clearUserCache(adminId);
        return true;
    }
}
