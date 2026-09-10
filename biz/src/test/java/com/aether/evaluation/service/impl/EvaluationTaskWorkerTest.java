package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationExperimentService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationTaskService;
import com.aether.evaluation.service.EvaluationWorkerSlotService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationTaskWorkerTest {
    @Test
    void recoversAnExpiredClaimBeforeAnExternalDispatch() {
        EvaluationTaskService taskService = mock(EvaluationTaskService.class);
        EvaluationWorkerSlotService slots = mock(EvaluationWorkerSlotService.class);
        EvaluationTask expired = new EvaluationTask(); expired.setId("task-1"); expired.setPhase("EXECUTE"); expired.setStatus("CLAIMED"); expired.setLeaseUntil(System.currentTimeMillis() - 1);
        when(taskService.list(any())).thenReturn(List.of(expired));
        when(taskService.claim(any(), anyLong())).thenReturn(null);

        worker(taskService, slots).claimOne();

        assertEquals("READY", expired.getStatus());
        verify(taskService).updateById(expired);
        verify(slots).releaseByTask("task-1");
    }

    @Test
    void marksAnExpiredSendingClaimUnknownInsteadOfRedispatchingIt() {
        EvaluationTaskService taskService = mock(EvaluationTaskService.class);
        EvaluationWorkerSlotService slots = mock(EvaluationWorkerSlotService.class);
        EvaluationResultService results = mock(EvaluationResultService.class);
        EvaluationExperimentAggregationService aggregation = mock(EvaluationExperimentAggregationService.class);
        EvaluationTask expired = new EvaluationTask(); expired.setId("task-1"); expired.setResultId("result-1"); expired.setPhase("EXECUTE"); expired.setStatus("CLAIMED"); expired.setDispatchState("SENDING"); expired.setLeaseUntil(System.currentTimeMillis() - 1);
        EvaluationResult result = new EvaluationResult(); result.setId("result-1"); result.setExperimentId("experiment-1");
        when(taskService.list(any())).thenReturn(List.of(expired));
        when(taskService.claim(any(), anyLong())).thenReturn(null);
        when(results.getById("result-1")).thenReturn(result);

        worker(taskService, slots, results, aggregation).claimOne();

        assertEquals("DONE", expired.getStatus());
        assertEquals("UNKNOWN", result.getExecutionStatus());
        verify(aggregation).refresh("experiment-1");
    }

    private EvaluationTaskWorker worker(EvaluationTaskService tasks, EvaluationWorkerSlotService slots) {
        return worker(tasks, slots, mock(EvaluationResultService.class), mock(EvaluationExperimentAggregationService.class));
    }

    private EvaluationTaskWorker worker(EvaluationTaskService tasks, EvaluationWorkerSlotService slots, EvaluationResultService results, EvaluationExperimentAggregationService aggregation) {
        return new EvaluationTaskWorker(tasks, results, mock(EvaluationCaseVersionService.class), mock(EvaluationGradingService.class), aggregation, mock(EvaluationExperimentService.class), slots);
    }
}
