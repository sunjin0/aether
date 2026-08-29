package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.service.AgentWorkflowVersionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowOutputResolverTest {
    @Test
    void returnsOnlyFieldsDeclaredByPublishedOutputSchema() {
        AgentWorkflowVersionService versions = mock(AgentWorkflowVersionService.class);
        AgentWorkflowVersion version = new AgentWorkflowVersion();
        version.setOutputSchema("[{\"name\":\"result\"}]");
        when(versions.getById("version-1")).thenReturn(version);
        AgentWorkflowInstance instance = new AgentWorkflowInstance();
        instance.setWorkflowVersionId("version-1");
        instance.setVariables("{\"result\":\"approved\",\"internalToken\":\"must-not-leak\"}");

        Map<String, Object> outputs = new WorkflowOutputResolver(versions).resolve(instance);

        assertEquals("approved", outputs.get("result"));
        assertFalse(outputs.containsKey("internalToken"));
    }
}
