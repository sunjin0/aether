package com.aether.evaluation.service;
public interface EvaluationResultCallbackService {
    boolean complete(String runId, String outputJson, String evidenceJson, String metricsJson);
    boolean fail(String runId, String errorCode, String errorMessage);
    /** Marks an interactive run as unable to finish unattended by an evaluation worker. */
    boolean block(String runId, String errorCode);
}
