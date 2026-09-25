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
import com.aether.local.CurrentUser;
import com.aether.sys.service.AccountDataScopeService;
import org.apache.commons.lang3.StringUtils;

@Service
public class EvaluationPolicyServiceImpl extends ServiceImpl<EvaluationPolicyMapper, EvaluationPolicy>
        implements EvaluationPolicyService {
    @Autowired(required = false) @Lazy private EvaluationGateService gateService;
    @Autowired(required = false) private EvaluationSnapshotService snapshotService;
    @Autowired(required = false) private AccountDataScopeService dataScopeService;
    @Override
    public boolean allowedToPublish(String targetType, String targetId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvaluationPolicy> query = Wrappers.lambdaQuery(EvaluationPolicy.class)
                .eq(EvaluationPolicy::getTargetType, targetType)
                .eq(EvaluationPolicy::getTargetId, targetId)
                .eq(EvaluationPolicy::getDeleted, false);
        if (dataScopeService != null && StringUtils.isNotBlank(CurrentUser.userId())) {
            query.in(EvaluationPolicy::getCreatedBy, dataScopeService.readableCreatorIds(null));
        }
        EvaluationPolicy policy = getOne(query, false);
        if (policy == null || !Boolean.TRUE.equals(policy.getRequired())) return true;
        if (("AGENT".equals(targetType) || "WORKFLOW".equals(targetType)) && gateService != null && snapshotService != null) {
            String snapshotOwner = StringUtils.defaultIfBlank(CurrentUser.userId(), policy.getCreatedBy());
            EvaluationTargetSnapshot snapshot = snapshotService.createCurrentSnapshot(targetType, targetId, snapshotOwner);
            Object allowed = gateService.check(targetType, targetId, snapshot.getFingerprint()).get("allowed");
            return Boolean.TRUE.equals(allowed);
        }
        // Legacy SKILL policies retain their existing evidence semantics; they are not eligible for the new evaluation gate.
        if (!"PASSED".equals(policy.getLastStatus()) || policy.getLastScore() == null) return false;
        return policy.getLastScore() >= (policy.getMinimumScore() == null ? 0 : policy.getMinimumScore());
    }
}
