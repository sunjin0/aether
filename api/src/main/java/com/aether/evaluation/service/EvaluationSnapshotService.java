package com.aether.evaluation.service;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
public interface EvaluationSnapshotService {
    EvaluationTargetSnapshot getById(String id);
    EvaluationTargetSnapshot create(String targetType, String targetId, String sourceKind, String sourceVersionId, String snapshotJson, String createdBy);
    EvaluationTargetSnapshot createCurrentSnapshot(String targetType, String targetId, String createdBy);
}
