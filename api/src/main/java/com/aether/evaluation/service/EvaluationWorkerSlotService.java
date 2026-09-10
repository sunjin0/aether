package com.aether.evaluation.service;

import com.aether.evaluation.entity.EvaluationWorkerSlot;
import com.baomidou.mybatisplus.extension.service.IService;

public interface EvaluationWorkerSlotService extends IService<EvaluationWorkerSlot> {
    EvaluationWorkerSlot claim(String phase, String taskId, long leaseMillis);
    void releaseByTask(String taskId);
}
