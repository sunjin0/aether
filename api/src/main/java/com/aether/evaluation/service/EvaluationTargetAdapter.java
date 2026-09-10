package com.aether.evaluation.service;

import com.aether.evaluation.entity.EvaluationTask;

/** Execution boundary for Agent and Workflow evaluation targets. */
public interface EvaluationTargetAdapter {
    boolean supports(EvaluationTask task);
    void dispatch(EvaluationTask task);
    void cancel(String resultId);
}
