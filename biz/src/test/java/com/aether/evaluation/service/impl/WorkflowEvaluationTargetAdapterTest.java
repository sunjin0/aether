package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationSnapshotService;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowEvaluationTargetAdapterTest {
    @Test
    void dispatchStartsAnEvaluationSnapshotAndPersistsTheResultAssociation() {
        AgentWorkflowExecutionService executionService = mock(AgentWorkflowExecutionService.class);
        EvaluationResultService resultService = mock(EvaluationResultService.class);
        EvaluationCaseVersionService caseService = mock(EvaluationCaseVersionService.class);
        EvaluationSnapshotService snapshotService = mock(EvaluationSnapshotService.class);
        WorkflowEvaluationTargetAdapter adapter = new WorkflowEvaluationTargetAdapter(executionService, resultService, caseService, snapshotService);
        EvaluationTask task = new EvaluationTask(); task.setPhase("EXECUTE"); task.setTargetType("WORKFLOW"); task.setResultId("result-1"); task.setSnapshotId("snapshot-1");
        EvaluationResult result = new EvaluationResult(); result.setId("result-1"); result.setCaseVersionId("case-1");
        EvaluationCaseVersion evaluationCase = new EvaluationCaseVersion(); evaluationCase.setId("case-1"); evaluationCase.setInputJson("{\"input\":\"hello\"}");
        EvaluationTargetSnapshot snapshot = new EvaluationTargetSnapshot();
        snapshot.setId("snapshot-1"); snapshot.setTargetType("WORKFLOW");
        snapshot.setSnapshotJson("{\"inputSchema\":[{\"name\":\"input\",\"required\":true}]}");
        AgentWorkflowInstance instance = new AgentWorkflowInstance(); instance.setId("workflow-instance-1");
        when(resultService.getById("result-1")).thenReturn(result);
        when(caseService.getById("case-1")).thenReturn(evaluationCase);
        when(snapshotService.getById("snapshot-1")).thenReturn(snapshot);
        when(executionService.startEvaluation(eq("snapshot-1"), any(Map.class), eq("evaluation"), eq("result-1"))).thenReturn(instance);

        adapter.dispatch(task);

        verify(executionService).startEvaluation("snapshot-1", Map.of("input", "hello"), "evaluation", "result-1");
        assertEquals("workflow-instance-1", result.getWorkflowInstanceId());
        assertEquals("RUNNING", result.getExecutionStatus());
        verify(resultService).updateById(result);
    }

    @Test
    void cancelTerminatesTheAssociatedWorkflowInstance() {
        AgentWorkflowExecutionService executionService = mock(AgentWorkflowExecutionService.class);
        EvaluationResultService resultService = mock(EvaluationResultService.class);
        WorkflowEvaluationTargetAdapter adapter = new WorkflowEvaluationTargetAdapter(executionService, resultService, mock(EvaluationCaseVersionService.class), mock(EvaluationSnapshotService.class));
        EvaluationResult result = new EvaluationResult(); result.setExecutionStatus("RUNNING"); result.setWorkflowInstanceId("workflow-instance-1");
        when(resultService.getById("result-1")).thenReturn(result);

        adapter.cancel("result-1");

        verify(executionService).terminate("workflow-instance-1", "evaluation");
    }

    @Test
    void rejectsUndeclaredCaseInputBeforeStartingTheWorkflow() {
        AgentWorkflowExecutionService executionService = mock(AgentWorkflowExecutionService.class);
        EvaluationResultService resultService = mock(EvaluationResultService.class);
        EvaluationCaseVersionService caseService = mock(EvaluationCaseVersionService.class);
        EvaluationSnapshotService snapshotService = mock(EvaluationSnapshotService.class);
        WorkflowEvaluationTargetAdapter adapter = new WorkflowEvaluationTargetAdapter(executionService, resultService, caseService, snapshotService);
        EvaluationTask task = new EvaluationTask(); task.setResultId("result-1"); task.setSnapshotId("snapshot-1");
        EvaluationResult result = new EvaluationResult(); result.setCaseVersionId("case-1");
        EvaluationCaseVersion evaluationCase = new EvaluationCaseVersion(); evaluationCase.setInputJson("{\"input\":\"hello\"}");
        EvaluationTargetSnapshot snapshot = new EvaluationTargetSnapshot(); snapshot.setTargetType("WORKFLOW"); snapshot.setSnapshotJson("{\"inputSchema\":[{\"name\":\"input_1\",\"required\":true}]}");
        when(resultService.getById("result-1")).thenReturn(result);
        when(caseService.getById("case-1")).thenReturn(evaluationCase);
        when(snapshotService.getById("snapshot-1")).thenReturn(snapshot);

        EvaluationPreDispatchException error = assertThrows(EvaluationPreDispatchException.class, () -> adapter.dispatch(task));

        assertEquals("WORKFLOW_INPUT_INVALID", error.getErrorCode());
        verifyNoInteractions(executionService);
    }
}
