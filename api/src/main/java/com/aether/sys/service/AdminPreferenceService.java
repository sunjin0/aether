package com.aether.sys.service;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;

public interface AdminPreferenceService extends IService<AdminPreference> {

    String buildPreferenceContext(String adminId, String taskType);

    AdminPreference getEffectivePreference(String adminId, String keyName, String taskType);

    void incrementUsage(String preferenceId);

    void adjustConfidence(String preferenceId, BigDecimal delta);

    void updateEffectiveScore(String preferenceId);

    List<AdminPreference> listByAdminId(String adminId);

    boolean clearUserCache(String adminId);
}
