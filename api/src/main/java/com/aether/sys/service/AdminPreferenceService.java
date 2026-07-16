package com.aether.sys.service;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AdminPreferenceService extends IService<AdminPreference> {

    /**
     * Build a compact system-context block for enabled preferences.
     */
    String buildPreferenceContext(String adminId);
}
