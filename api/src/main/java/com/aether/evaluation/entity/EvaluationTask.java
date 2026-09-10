package com.aether.evaluation.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("evaluation_task")
public class EvaluationTask extends BaseEntity { private String resultId, phase, status, leaseOwner, leaseToken, dispatchState, errorCode, targetType, targetId, snapshotId; private Integer gradingRound, attemptCount; private Long leaseUntil, nextRunAt, deadlineAt; }
