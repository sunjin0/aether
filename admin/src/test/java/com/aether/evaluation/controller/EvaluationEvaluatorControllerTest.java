package com.aether.evaluation.controller;

import com.aether.evaluation.service.EvaluationEvaluatorService;
import com.aether.evaluation.service.EvaluationEvaluatorVersionService;
import com.aether.evaluation.service.EvaluationDatasetService;
import com.aether.evaluation.service.EvaluationCaseService;
import com.aether.evaluation.service.EvaluationDatasetVersionService;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.entity.EvaluationCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;

class EvaluationEvaluatorControllerTest {
    @Test
    void jsonPathRuleOnlyAcceptsTheRestrictedPathGrammar() {
        EvaluationEvaluatorController controller = new EvaluationEvaluatorController(
                mock(EvaluationEvaluatorService.class), mock(EvaluationEvaluatorVersionService.class));

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(controller, "validConfig", "RULE",
                "{\"operator\":\"JSON_PATH_EQUALS\",\"jsonPath\":\"$.data.items[0].status\"}"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(controller, "validConfig", "RULE",
                "{\"operator\":\"JSON_PATH_EQUALS\",\"jsonPath\":\"$.data.items[?(@.status)]\"}"));
    }

    @Test
    void jsonSchemaRuleAcceptsTheStructuredSchemaStoredByTheUi() {
        EvaluationEvaluatorController controller = new EvaluationEvaluatorController(
                mock(EvaluationEvaluatorService.class), mock(EvaluationEvaluatorVersionService.class));

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(controller, "validConfig", "RULE",
                "{\"operator\":\"JSON_SCHEMA\",\"schema\":{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\"}}}}"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(controller, "validConfig", "RULE",
                "{\"operator\":\"JSON_SCHEMA\",\"schema\":{\"$ref\":\"https://example.test/schema.json\"}}"));
    }

    @Test
    void datasetPublishOnlyAcceptsAssertionsForItsTargetType() {
        EvaluationDatasetController controller = new EvaluationDatasetController(
                mock(EvaluationDatasetService.class), mock(EvaluationCaseService.class),
                mock(EvaluationDatasetVersionService.class), mock(EvaluationCaseVersionService.class),
                mock(EvaluationEvaluatorVersionService.class));
        EvaluationCase toolCase = new EvaluationCase();
        toolCase.setAssertionsJson("[{\"type\":\"TOOL_CALLED\",\"target\":\"tool-1\"}]");
        assertTrue(ReflectionTestUtils.invokeMethod(controller, "validateAssertions", "AGENT", List.of(toolCase)) == null);

        EvaluationCase nodeCase = new EvaluationCase();
        nodeCase.setAssertionsJson("[{\"type\":\"NODE_VISITED\",\"target\":\"node-1\"}]");
        assertEquals("agent.evaluation.case.assertions.invalid", ReflectionTestUtils.invokeMethod(controller,
                "validateAssertions", "AGENT", List.of(nodeCase)));
    }
}
