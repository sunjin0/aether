package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationExperiment;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationExperimentService;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.aether.evaluation.service.EvaluationResultService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationExperimentAggregationServiceTest {
    @Test
    void assertionFailureDoesNotCountAsAPassingCaseOrExperiment() {
        EvaluationExperimentService experiments = mock(EvaluationExperimentService.class);
        EvaluationResultService results = mock(EvaluationResultService.class);
        EvaluationPolicyService policies = mock(EvaluationPolicyService.class);
        EvaluationCaseVersionService cases = mock(EvaluationCaseVersionService.class);
        EvaluationExperiment experiment = new EvaluationExperiment();
        experiment.setId("experiment-1"); experiment.setStatus("RUNNING");
        experiment.setConfigJson("{\"minimumScore\":80,\"minimumPassRate\":100}");
        EvaluationCaseVersion evaluationCase = new EvaluationCaseVersion(); evaluationCase.setId("case-version-1"); evaluationCase.setPassThreshold(80);
        EvaluationResult result = new EvaluationResult();
        result.setCaseVersionId("case-version-1"); result.setExecutionStatus("SUCCEEDED"); result.setGradingStatus("SUCCEEDED");
        result.setQualityStatus("FAILED"); result.setScore(BigDecimal.valueOf(100)); result.setMetricsJson("{\"latencyMs\":20}");
        when(experiments.getById("experiment-1")).thenReturn(experiment);
        when(results.list(any())).thenReturn(List.of(result));
        when(cases.listByIds(any())).thenReturn(List.of(evaluationCase));
        when(policies.getOne(any(), eq(false))).thenReturn(null);

        new EvaluationExperimentAggregationService(experiments, results, policies, cases).refresh("experiment-1");

        assertEquals("FAILED", experiment.getQualityStatus());
        org.assertj.core.api.Assertions.assertThat(experiment.getMetricsJson()).contains("\"casePassRate\":0.00");
    }
}
