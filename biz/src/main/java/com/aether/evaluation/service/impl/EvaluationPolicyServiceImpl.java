package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationPolicy;
import com.aether.evaluation.mapper.EvaluationPolicyMapper;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EvaluationPolicyServiceImpl extends ServiceImpl<EvaluationPolicyMapper, EvaluationPolicy>
        implements EvaluationPolicyService {
    @Override
    public boolean allowedToPublish(String targetType, String targetId) {
        EvaluationPolicy policy = getOne(Wrappers.lambdaQuery(EvaluationPolicy.class)
                .eq(EvaluationPolicy::getTargetType, targetType)
                .eq(EvaluationPolicy::getTargetId, targetId)
                .eq(EvaluationPolicy::getDeleted, false), false);
        if (policy == null || !Boolean.TRUE.equals(policy.getRequired())) return true;
        if (!"PASSED".equals(policy.getLastStatus()) || policy.getLastScore() == null) return false;
        return policy.getLastScore() >= (policy.getMinimumScore() == null ? 0 : policy.getMinimumScore());
    }
}
