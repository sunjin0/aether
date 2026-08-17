package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 实现管理员偏好业务服务。
 */
@Service
public class AdminPreferenceServiceImpl extends ServiceImpl<AdminPreferenceMapper, AdminPreference>
        implements AdminPreferenceService {

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    @Autowired
    private AdminPreferenceEventService preferenceEventService;

    /**
     * 构建偏好Context。
     */
    @Override
    public String buildPreferenceContext(String adminId, String taskType) {
        return reasoningEngine.buildPreferenceContext(adminId, taskType);
    }

    /**
     * 构建偏好Context。
     */
    @Override
    public String buildPreferenceContext(String adminId, String taskType, String conversationId) {
        return reasoningEngine.buildPreferenceContext(adminId, taskType, conversationId);
    }

    /**
     * 获取Effective偏好。
     */
    @Override
    public AdminPreference getEffectivePreference(String adminId, String keyName, String taskType) {
        List<AdminPreference> effective = reasoningEngine.resolveEffectivePreferences(adminId, taskType);
        return effective.stream()
                .filter(p -> p.getKeyName().equals(keyName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 处理incrementUsage。
     */
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
            super.updateById(pref);
            updateEffectiveScore(preferenceId);
        }
    }

    /**
     * 处理adjustConfidence。
     */
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

            super.updateById(pref);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    /**
     * 更新EffectiveScore。
     */
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
            super.updateById(pref);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    /**
     * 查询按管理员Id。
     */
    @Override
    public List<AdminPreference> listByAdminId(String adminId) {
        return list(Wrappers.lambdaQuery(AdminPreference.class)
                .eq(AdminPreference::getAdminId, adminId)
                .eq(AdminPreference::getDeleted, false)
                .orderByDesc(AdminPreference::getEffectiveScore));
    }

    /**
     * 处理clear用户缓存。
     */
    @Override
    public boolean clearUserCache(String adminId) {
        reasoningEngine.clearUserCache(adminId);
        return true;
    }

    /**
     * 处理reconcileAfterEvidenceRemoval。
     */
    @Override
    public void reconcileAfterEvidenceRemoval(Collection<String> preferenceIds) {
        if (preferenceIds == null || preferenceIds.isEmpty()) {
            return;
        }
        Set<String> uniqueIds = new HashSet<String>(preferenceIds);
        for (String preferenceId : uniqueIds) {
            AdminPreference preference = super.getById(preferenceId);
            if (preference == null
                    || AdminPreference.SOURCE_EXPLICIT.equals(preference.getSource())) {
                continue;
            }
            long remainingEvidence = preferenceEventService.count(
                    Wrappers.lambdaQuery(AdminPreferenceEvent.class)
                            .eq(AdminPreferenceEvent::getPreferenceId, preferenceId)
                            .eq(AdminPreferenceEvent::getEventType,
                                    AdminPreferenceEvent.EVENT_EXTRACT)
                            .eq(AdminPreferenceEvent::getDeleted, false));
            long explicitFeedback = preferenceEventService.count(
                    Wrappers.lambdaQuery(AdminPreferenceEvent.class)
                            .eq(AdminPreferenceEvent::getPreferenceId, preferenceId)
                            .in(AdminPreferenceEvent::getEventType,
                                    AdminPreferenceEvent.EVENT_CONFIRM,
                                    AdminPreferenceEvent.EVENT_OVERRIDE)
                            .eq(AdminPreferenceEvent::getDeleted, false));
            if (remainingEvidence == 0L && explicitFeedback == 0L) {
                preference.setStatus(AdminPreference.STATUS_DISABLED);
                preference.setConfidence(BigDecimal.ZERO);
                preference.setEffectiveScore(BigDecimal.ZERO);
                super.updateById(preference);
            }
        }
    }

    /**
     * 保存当前请求。
     */
    @Override
    public boolean save(AdminPreference preference) {
        normalizeForSave(preference);
        boolean saved = super.save(preference);
        if (saved) {
            reasoningEngine.clearUserCache(preference.getAdminId());
        }
        return saved;
    }

    /**
     * 更新按Id。
     */
    @Override
    public boolean updateById(AdminPreference preference) {
        AdminPreference existing = preference == null || preference.getId() == null
                ? null : super.getById(preference.getId());
        normalizeForUpdate(preference);
        boolean updated = super.updateById(preference);
        if (updated && existing != null) {
            reasoningEngine.clearUserCache(existing.getAdminId());
        }
        return updated;
    }

    /**
     * 移除按Id。
     */
    @Override
    public boolean removeById(Serializable id) {
        AdminPreference existing = id == null ? null : super.getById(id);
        boolean removed = super.removeById(id);
        if (removed && existing != null) {
            reasoningEngine.clearUserCache(existing.getAdminId());
        }
        return removed;
    }

    /**
     * 规范化用于保存。
     */
    private void normalizeForSave(AdminPreference preference) {
        if (preference == null) {
            return;
        }
        if (StringUtils.isBlank(preference.getScope())) {
            preference.setScope(AdminPreference.SCOPE_GLOBAL);
        }
        if (preference.getScopeDetail() == null) {
            preference.setScopeDetail("");
        }
        normalizeForUpdate(preference);
    }

    /**
     * 规范化用于更新。
     */
    private void normalizeForUpdate(AdminPreference preference) {
        if (preference == null) {
            return;
        }
        if (StringUtils.isNotBlank(preference.getCategory())) {
            preference.setCategory(preference.getCategory().trim().toLowerCase());
        }
        if (StringUtils.isNotBlank(preference.getKeyName())) {
            preference.setKeyName(preference.getKeyName().trim().toLowerCase());
        }
        if (StringUtils.isNotBlank(preference.getScope())) {
            preference.setScope(preference.getScope().trim().toLowerCase());
        }
        if (preference.getScopeDetail() != null) {
            preference.setScopeDetail(preference.getScopeDetail().trim());
        }
    }
}
