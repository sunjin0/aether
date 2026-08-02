package com.aether.workflow.runtime;

import com.aether.exception.ServerException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionValidatorTest {

    private static final String NODES = "["
            + "{\"id\":\"start\",\"type\":\"start\"},"
            + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"处理 ${request}\",\"outputKey\":\"result\"},"
            + "{\"id\":\"next\",\"type\":\"agent\",\"resourceId\":\"agent-2\",\"prompt\":\"汇总 ${result}\"},"
            + "{\"id\":\"end\",\"type\":\"end\"}]";
    private static final String EDGES = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"next\"},{\"source\":\"next\",\"target\":\"end\"}]";

    @Test
    void acceptsDeclaredInputAndNodeOutputReferences() {
        String schema = "[{\"name\":\"request\",\"required\":true}]";
        String nodes = NODES;

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, EDGES, schema));
    }

    @Test
    void rejectsUnknownVariableReferenceAtPublishTime() {
        String nodes = NODES.replace("${request}", "${missing}");

        assertThrows(ServerException.class,
                () -> WorkflowDefinitionValidator.validateVariables(nodes, EDGES, "[{\"name\":\"request\"}]"));
    }

    @Test
    void rejectsMissingRequiredOrUndeclaredStartInput() {
        String schema = "[{\"name\":\"request\",\"required\":true}]";

        assertThrows(ServerException.class,
                () -> WorkflowDefinitionValidator.validateStartVariables(schema, Collections.<String, Object>emptyMap()));
        Map<String, Object> unknown = new HashMap<String, Object>();
        unknown.put("other", "value");
        assertThrows(ServerException.class,
                () -> WorkflowDefinitionValidator.validateStartVariables(schema, unknown));
    }

    @Test
    void acceptsOutputDeclaredOnEveryPathAndRejectsInternalOrBranchOnlyValues() {
        String output = "[{\"name\":\"result\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateOutputSchema(NODES, EDGES,
                "[{\"name\":\"request\"}]", output));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateOutputSchema(NODES, EDGES,
                "[{\"name\":\"request\"}]", "[{\"name\":\"unknown\"}]"));
    }
}
