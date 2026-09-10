package com.aether.evaluation.service;
import com.aether.evaluation.entity.EvaluationTask; import com.baomidou.mybatisplus.extension.service.IService;
public interface EvaluationTaskService extends IService<EvaluationTask> {
    EvaluationTask claim(String workerId, long leaseMillis);
}
