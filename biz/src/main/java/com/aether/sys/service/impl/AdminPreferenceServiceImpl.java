package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminPreferenceServiceImpl extends ServiceImpl<AdminPreferenceMapper, AdminPreference>
        implements AdminPreferenceService {

    private static final int STATUS_ENABLED = 1;
    private static final int MAX_CONTEXT_ITEMS = 20;

    @Override
    public String buildPreferenceContext(String adminId) {
        if (StringUtils.isBlank(adminId)) {
            return null;
        }
        List<AdminPreference> preferences = list(Wrappers.lambdaQuery(AdminPreference.class)
                .eq(AdminPreference::getAdminId, adminId)
                .eq(AdminPreference::getStatus, STATUS_ENABLED)
                .eq(AdminPreference::getDeleted, false)
                .orderByDesc(AdminPreference::getUpdatedAt)
                .last("LIMIT " + MAX_CONTEXT_ITEMS));
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("【后台用户长期偏好】\n");
        for (AdminPreference preference : preferences) {
            if (StringUtils.isBlank(preference.getContent())) {
                continue;
            }
            builder.append("- ");
            if (StringUtils.isNotBlank(preference.getCategory())) {
                builder.append('[').append(preference.getCategory()).append("] ");
            }
            builder.append(preference.getContent()).append('\n');
        }
        return builder.toString();
    }
}
