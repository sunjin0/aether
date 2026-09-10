package com.aether.evaluation.service;

/** Deletes evaluation resources and the data owned by them. */
public interface EvaluationDataDeletionService {
    void deleteDataset(String datasetId);
    void deleteEvaluator(String evaluatorId);
    void deleteExperiment(String experimentId);
}
