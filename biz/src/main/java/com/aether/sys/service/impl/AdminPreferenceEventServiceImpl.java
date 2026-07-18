package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceEventMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AdminPreferenceEventServiceImpl extends ServiceImpl<AdminPreferenceEventMapper, AdminPreferenceEvent>
        implements AdminPreferenceEventService {

    @Override
    public void logEvent(AdminPreferenceEvent event) {
        if (event == null) {
            return;
        }
        save(event);
    }
}
