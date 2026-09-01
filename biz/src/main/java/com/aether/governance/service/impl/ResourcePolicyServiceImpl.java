package com.aether.governance.service.impl;

import com.aether.governance.entity.ResourcePolicyRule;
import com.aether.governance.mapper.ResourcePolicyRuleMapper;
import com.aether.governance.service.ResourcePolicyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

@Service
public class ResourcePolicyServiceImpl extends ServiceImpl<ResourcePolicyRuleMapper, ResourcePolicyRule>
        implements ResourcePolicyService {
    @Override
    public boolean allowed(String subjectType, String subjectId, String resourceType, String resourceId, String action) {
        return allowed(subjectType, subjectId, resourceType, resourceId, action, null);
    }

    @Override
    public boolean allowed(String subjectType, String subjectId, String resourceType, String resourceId,
                           String action, Map<String, Object> context) {
        List<ResourcePolicyRule> rules = list(Wrappers.lambdaQuery(ResourcePolicyRule.class)
                .eq(ResourcePolicyRule::getSubjectType, subjectType).eq(ResourcePolicyRule::getSubjectId, subjectId)
                .eq(ResourcePolicyRule::getResourceType, resourceType).eq(ResourcePolicyRule::getResourceId, resourceId)
                .eq(ResourcePolicyRule::getAction, action).eq(ResourcePolicyRule::getDeleted, false));
        if (rules.isEmpty()) return true;
        for (ResourcePolicyRule rule : rules) {
            if (!matchesContext(rule, context)) continue;
            if ("DENY".equalsIgnoreCase(rule.getEffect())) return false;
        }
        return rules.stream().anyMatch(rule -> "ALLOW".equalsIgnoreCase(rule.getEffect()) && matchesContext(rule, context));
    }

    private boolean matchesContext(ResourcePolicyRule rule, Map<String, Object> context) {
        if (rule.getConditionJson() == null || rule.getConditionJson().trim().isEmpty()) return true;
        if (context == null) return false;
        try {
            JSONObject conditions = JSON.parseObject(rule.getConditionJson());
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                Object actual = context.get(entry.getKey());
                if (actual == null || !String.valueOf(actual).equals(String.valueOf(entry.getValue()))) return false;
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
