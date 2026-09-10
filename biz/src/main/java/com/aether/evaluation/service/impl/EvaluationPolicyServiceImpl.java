package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationPolicy;
import com.aether.evaluation.mapper.EvaluationPolicyMapper;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.aether.evaluation.service.EvaluationGateService;
import com.aether.evaluation.service.EvaluationSnapshotService;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

@Service
public class EvaluationPolicyServiceImpl extends ServiceImpl<EvaluationPolicyMapper, EvaluationPolicy>
        implements EvaluationPolicyService {
    @Autowired(required = false) @Lazy private EvaluationGateService gateService;
    @Autowired(required = false) private EvaluationSnapshotService snapshotService;
    @Override
    public boolean allowedToPublish(String targetType, String targetId) {
        EvaluationPolicy policy = getOne(Wrappers.lambdaQuery(EvaluationPolicy.class)
                .eq(EvaluationPolicy::getTargetType, targetType)
                .eq(EvaluationPolicy::getTargetId, targetId)
                .eq(EvaluationPolicy::getDeleted, false), false);
        if (policy == null || !Boolean.TRUE.equals(policy.getRequired())) return true;
        if (("AGENT".equals(targetType) || "WORKFLOW".equals(targetType)) && gateService != null && snapshotService != null) {
            EvaluationTargetSnapshot snapshot = snapshotService.createCurrentSnapshot(targetType, targetId, "evaluation-gate");
            Object allowed = gateService.check(targetType, targetId, snapshot.getFingerprint()).get("allowed");
            return Boolean.TRUE.equals(allowed);
        }
        // Legacy SKILL policies retain their existing evidence semantics; they are not eligible for the new evaluation gate.
        if (!"PASSED".equals(policy.getLastStatus()) || policy.getLastScore() == null) return false;
        return policy.getLastScore() >= (policy.getMinimumScore() == null ? 0 : policy.getMinimumScore());
    }
}
