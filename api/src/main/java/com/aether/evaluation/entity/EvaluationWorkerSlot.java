package com.aether.evaluation.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Durable cross-instance capacity slot for evaluation execution or grading. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_worker_slot")
public class EvaluationWorkerSlot extends BaseEntity {
    private String phase;
    private Integer slotNo;
    private String taskId;
    private String leaseToken;
    private Long leaseUntil;
}
