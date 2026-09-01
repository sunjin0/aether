package com.aether.governance.service;

import com.aether.governance.entity.ResourcePolicyRule;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface ResourcePolicyService extends IService<ResourcePolicyRule> {
    boolean allowed(String subjectType, String subjectId, String resourceType, String resourceId, String action);

    /** Evaluate the same policy with request/application data-scope claims. */
    boolean allowed(String subjectType, String subjectId, String resourceType, String resourceId,
                    String action, Map<String, Object> context);
}
