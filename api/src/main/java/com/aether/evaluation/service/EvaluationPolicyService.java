package com.aether.evaluation.service;

import com.aether.evaluation.entity.EvaluationPolicy;
import com.baomidou.mybatisplus.extension.service.IService;

public interface EvaluationPolicyService extends IService<EvaluationPolicy> {
    boolean allowedToPublish(String targetType, String targetId);
}
