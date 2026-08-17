package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceEventMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现管理员偏好事件业务服务。
 */
@Service
public class AdminPreferenceEventServiceImpl extends ServiceImpl<AdminPreferenceEventMapper, AdminPreferenceEvent>
        implements AdminPreferenceEventService {

    /**
     * 处理log事件。
     */
    @Override
    public void logEvent(AdminPreferenceEvent event) {
        if (event == null) {
            return;
        }
        save(event);
    }

    /**
     * 获取Last事件。
     */
    @Override
    public AdminPreferenceEvent getLastEvent(String adminId, String eventType) {
        return getLastEvent(adminId, eventType, null);
    }

    /**
     * 获取Last事件。
     */
    @Override
    public AdminPreferenceEvent getLastEvent(String adminId, String eventType, String conversationId) {
        return getOne(new LambdaQueryWrapper<AdminPreferenceEvent>()
                .eq(AdminPreferenceEvent::getAdminId, adminId)
                .eq(AdminPreferenceEvent::getEventType, eventType)
                .eq(conversationId != null, AdminPreferenceEvent::getConversationId, conversationId)
                .orderByDesc(AdminPreferenceEvent::getCreatedAt)
                .orderByDesc(AdminPreferenceEvent::getId)
                .last("LIMIT 1"));
    }
}
