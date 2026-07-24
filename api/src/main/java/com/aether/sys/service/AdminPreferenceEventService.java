package com.aether.sys.service;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AdminPreferenceEventService extends IService<AdminPreferenceEvent> {

    void logEvent(AdminPreferenceEvent event);

    AdminPreferenceEvent getLastEvent(String adminId, String eventType);

    AdminPreferenceEvent getLastEvent(String adminId, String eventType, String conversationId);
}
