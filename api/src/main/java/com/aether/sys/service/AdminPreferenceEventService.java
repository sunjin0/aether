package com.aether.sys.service;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义管理员偏好事件业务服务契约。
 */
public interface AdminPreferenceEventService extends IService<AdminPreferenceEvent> {

    /**
     * 处理log事件。
     */
    void logEvent(AdminPreferenceEvent event);

    /**
     * 获取Last事件。
     */
    AdminPreferenceEvent getLastEvent(String adminId, String eventType);

    /**
     * 获取Last事件。
     */
    AdminPreferenceEvent getLastEvent(String adminId, String eventType, String conversationId);
}
