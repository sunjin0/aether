package com.aether.sys.service;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 定义管理员偏好业务服务契约。
 */
public interface AdminPreferenceService extends IService<AdminPreference> {

    /**
     * 构建偏好Context。
     */
    String buildPreferenceContext(String adminId, String taskType);

    /**
     * 构建偏好Context。
     */
    String buildPreferenceContext(String adminId, String taskType, String conversationId);

    /**
     * 获取Effective偏好。
     */
    AdminPreference getEffectivePreference(String adminId, String keyName, String taskType);

    /**
     * 处理incrementUsage。
     */
    void incrementUsage(String preferenceId);

    /**
     * 处理adjustConfidence。
     */
    void adjustConfidence(String preferenceId, BigDecimal delta);

    /**
     * 更新EffectiveScore。
     */
    void updateEffectiveScore(String preferenceId);

    /**
     * 查询按管理员Id。
     */
    List<AdminPreference> listByAdminId(String adminId);

    /**
     * 处理clear用户缓存。
     */
    boolean clearUserCache(String adminId);

    /**
     * 处理reconcileAfterEvidenceRemoval。
     */
    void reconcileAfterEvidenceRemoval(Collection<String> preferenceIds);
}
