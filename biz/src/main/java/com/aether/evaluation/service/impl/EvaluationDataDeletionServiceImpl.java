package com.aether.evaluation.service.impl;

import com.aether.evaluation.service.EvaluationDataDeletionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes evaluation data in dependency order without touching non-evaluation records. */
@Service
public class EvaluationDataDeletionServiceImpl implements EvaluationDataDeletionService {
    private final JdbcTemplate jdbcTemplate;

    public EvaluationDataDeletionServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(String datasetId) {
        jdbcTemplate.queryForList("SELECT id FROM evaluation_experiment WHERE dataset_version_id IN (SELECT id FROM evaluation_dataset_version WHERE dataset_id = ?)", datasetId)
                .forEach(row -> deleteExperiment(String.valueOf(row.get("id"))));
        update("DELETE FROM evaluation_case_version WHERE dataset_version_id IN (SELECT id FROM evaluation_dataset_version WHERE dataset_id = ?)", datasetId);
        update("DELETE FROM evaluation_dataset_version WHERE dataset_id = ?", datasetId);
        update("DELETE FROM evaluation_case WHERE dataset_id = ?", datasetId);
        update("DELETE FROM evaluation_dataset WHERE id = ?", datasetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvaluator(String evaluatorId) {
        update("DELETE FROM evaluation_evaluator_version WHERE evaluator_id = ?", evaluatorId);
        update("DELETE FROM evaluation_evaluator WHERE id = ?", evaluatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExperiment(String experimentId) {
        update("UPDATE evaluation_worker_slot SET task_id = NULL, lease_token = NULL, lease_until = NULL WHERE task_id IN (SELECT id FROM evaluation_task WHERE result_id IN (SELECT id FROM evaluation_result WHERE experiment_id = ?))", experimentId);
        update("DELETE FROM evaluation_review WHERE experiment_id = ?", experimentId);
        update("DELETE FROM evaluation_baseline WHERE experiment_id = ?", experimentId);
        update("DELETE FROM evaluation_score WHERE result_id IN (SELECT id FROM evaluation_result WHERE experiment_id = ?)", experimentId);
        update("DELETE FROM evaluation_task WHERE result_id IN (SELECT id FROM evaluation_result WHERE experiment_id = ?)", experimentId);
        update("DELETE FROM evaluation_result WHERE experiment_id = ?", experimentId);
        update("DELETE FROM evaluation_experiment WHERE id = ?", experimentId);
    }

    private void update(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
    }
}
