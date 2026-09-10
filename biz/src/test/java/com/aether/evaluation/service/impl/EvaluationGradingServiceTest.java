package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationEvaluator;
import com.aether.evaluation.entity.EvaluationEvaluatorVersion;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.service.EvaluationEvaluatorService;
import com.aether.evaluation.service.EvaluationEvaluatorVersionService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationScoreService;
import com.aether.evaluation.service.LlmEvaluationGrader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationGradingServiceTest {
    @Test
    void hardToolAssertionOverridesAnOtherwisePassingRuleScore() {
        EvaluationScoreService scoreService = mock(EvaluationScoreService.class);
        EvaluationResultService resultService = mock(EvaluationResultService.class);
        EvaluationEvaluatorVersionService versionService = mock(EvaluationEvaluatorVersionService.class);
        EvaluationEvaluatorService evaluatorService = mock(EvaluationEvaluatorService.class);
        EvaluationEvaluatorVersion version = new EvaluationEvaluatorVersion();
        version.setId("version-1");
        version.setEvaluatorId("evaluator-1");
        version.setConfigJson("{\"operator\":\"CONTAINS\"}");
        EvaluationEvaluator evaluator = new EvaluationEvaluator();
        evaluator.setId("evaluator-1");
        evaluator.setKind("RULE");
        when(versionService.getById("version-1")).thenReturn(version);
        when(evaluatorService.getById("evaluator-1")).thenReturn(evaluator);
        when(scoreService.save(any())).thenReturn(true);
        when(resultService.updateById(any())).thenReturn(true);

        EvaluationGradingService service = new EvaluationGradingService(scoreService, resultService, versionService,
                evaluatorService, mock(LlmEvaluationGrader.class));
        EvaluationCaseVersion evaluationCase = new EvaluationCaseVersion();
        evaluationCase.setEvaluatorBindingsJson("[{\"evaluatorVersionId\":\"version-1\",\"expected\":\"accepted\",\"weight\":1}]");
        evaluationCase.setAssertionsJson("[{\"type\":\"TOOL_CALLED\",\"target\":\"tool-1\"}]");
        evaluationCase.setPassThreshold(80);
        EvaluationResult result = new EvaluationResult();
        result.setId("result-1");
        result.setOutputJson("{\"content\":\"accepted\"}");
        result.setEvidenceJson("{\"tools\":[{\"toolId\":\"tool-1\"}]}");

        service.grade(result, evaluationCase);
        assertEquals("PASSED", result.getQualityStatus());

        result.setEvidenceJson("{\"tools\":[]}");
        service.grade(result, evaluationCase);
        assertEquals("FAILED", result.getQualityStatus());
        assertEquals("ASSERTION_FAILED", result.getErrorCode());
    }

    @Test
    void successfulToolAssertionRequiresASuccessfulInvocation() {
        EvaluationScoreService scoreService = mock(EvaluationScoreService.class);
        EvaluationResultService resultService = mock(EvaluationResultService.class);
        EvaluationEvaluatorVersionService versionService = mock(EvaluationEvaluatorVersionService.class);
        EvaluationEvaluatorService evaluatorService = mock(EvaluationEvaluatorService.class);
        EvaluationEvaluatorVersion version = new EvaluationEvaluatorVersion();
        version.setId("version-1"); version.setEvaluatorId("evaluator-1"); version.setConfigJson("{\"operator\":\"CONTAINS\"}");
        EvaluationEvaluator evaluator = new EvaluationEvaluator(); evaluator.setId("evaluator-1"); evaluator.setKind("RULE");
        when(versionService.getById("version-1")).thenReturn(version); when(evaluatorService.getById("evaluator-1")).thenReturn(evaluator);
        when(scoreService.save(any())).thenReturn(true); when(resultService.updateById(any())).thenReturn(true);
        EvaluationGradingService service = new EvaluationGradingService(scoreService, resultService, versionService, evaluatorService, mock(LlmEvaluationGrader.class));
        EvaluationCaseVersion evaluationCase = new EvaluationCaseVersion();
        evaluationCase.setEvaluatorBindingsJson("[{\"evaluatorVersionId\":\"version-1\",\"expected\":\"accepted\",\"weight\":1}]");
        evaluationCase.setAssertionsJson("[{\"type\":\"TOOL_SUCCEEDED\",\"target\":\"tool-1\"}]");
        EvaluationResult result = new EvaluationResult(); result.setId("result-1"); result.setOutputJson("{\"content\":\"accepted\"}");
        result.setEvidenceJson("{\"tools\":[{\"toolId\":\"tool-1\",\"status\":\"FAILED\"}]}");

        service.grade(result, evaluationCase);
        assertEquals("FAILED", result.getQualityStatus());
        result.setEvidenceJson("{\"tools\":[{\"toolId\":\"tool-1\",\"status\":\"SUCCEEDED\"}]}");
        service.grade(result, evaluationCase);
        assertEquals("PASSED", result.getQualityStatus());
    }
}
