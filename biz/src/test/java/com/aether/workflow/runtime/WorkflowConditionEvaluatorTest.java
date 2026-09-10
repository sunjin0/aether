package com.aether.workflow.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowConditionEvaluatorTest {

    @Test
    void evaluatesEqualityWithSimpleVariablePath() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("exit", true);

        assertTrue(WorkflowConditionEvaluator.evaluate("${exit}==true", variables));
        assertFalse(WorkflowConditionEvaluator.evaluate("${exit}==false", variables));
    }

    @Test
    void evaluatesEqualityWithNestedVariablePath() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "done");
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("result", result);

        assertTrue(WorkflowConditionEvaluator.evaluate("${result.status} == \"done\"", variables));
    }
}
