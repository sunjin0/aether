package com.aether.evaluation.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_review")
public class EvaluationReview extends BaseEntity {
    private String experimentId, caseKey, resultId, reviewerId, decision, reason, previousReviewId;
    private Long reportRevision;
}
