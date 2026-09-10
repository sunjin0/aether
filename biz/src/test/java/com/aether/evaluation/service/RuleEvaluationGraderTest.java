package com.aether.evaluation.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluationGraderTest {
    @Test void equalsAndContainsAreDeterministic() {
        assertEquals("PASS", RuleEvaluationGrader.grade("ok", "EQUALS", "ok").getStatus());
        assertEquals(100, RuleEvaluationGrader.grade("order completed", "CONTAINS", "completed").getScore());
        assertEquals("PASS", RuleEvaluationGrader.grade("order completed", "contains", "completed").getStatus());
        assertEquals("FAIL", RuleEvaluationGrader.grade("ok", "EQUALS", "no").getStatus());
    }
    @Test void regexAndInvalidOperatorAreHandled() {
        assertEquals("PASS", RuleEvaluationGrader.grade("ID-42", "REGEX", "ID-[0-9]+").getStatus());
        assertEquals("ERROR", RuleEvaluationGrader.grade("x", "UNKNOWN", "x").getStatus());
        assertEquals("ERROR", RuleEvaluationGrader.grade("x", "REGEX", "[").getStatus());
        assertEquals("ERROR", RuleEvaluationGrader.grade("abab", "REGEX", "(ab)\\1").getStatus());
    }
    @Test void jsonPathOnlySupportsFieldsAndArrayIndexes() {
        String actual = "{\"data\":{\"items\":[{\"status\":\"READY\"}]}}";
        assertEquals("PASS", RuleEvaluationGrader.grade(actual, "JSON_PATH_EQUALS", "READY", "$.data.items[0].status").getStatus());
        assertEquals("FAIL", RuleEvaluationGrader.grade(actual, "JSON_PATH_EQUALS", "DONE", "$.data.items[0].status").getStatus());
        assertEquals("ERROR", RuleEvaluationGrader.grade(actual, "JSON_PATH_EQUALS", "READY", "$.data.items[?(@.status)]").getStatus());
        assertTrue(RuleEvaluationGrader.isSupportedJsonPath("$.data.items[0].status"));
        assertFalse(RuleEvaluationGrader.isSupportedJsonPath("$.data.items[?(@.status)]"));
    }

    @Test void jsonSchemaUsesDraftSevenAndRejectsRemoteReferences() {
        String schema = "{\"type\":\"object\",\"required\":[\"status\"],\"properties\":{\"status\":{\"type\":\"string\"}}}";
        assertEquals("PASS", RuleEvaluationGrader.grade("{\"status\":\"READY\"}", "JSON_SCHEMA", null, null, schema).getStatus());
        assertEquals("FAIL", RuleEvaluationGrader.grade("{\"status\":1}", "JSON_SCHEMA", null, null, schema).getStatus());
        assertFalse(RuleEvaluationGrader.isValidJsonSchema("{\"$ref\":\"https://example.test/schema.json\"}"));
    }
}
