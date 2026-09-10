package com.aether.evaluation.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.DeepAgentSigningClient;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationExperimentService;
import com.aether.evaluation.service.EvaluationResultCallbackService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationSnapshotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEvaluationTargetAdapterTest {
    @Test
    void deepEvaluationRetrievesOnlyKnowledgeBasesCapturedInSnapshot() {
        AgentDefinitionService agentService = mock(AgentDefinitionService.class);
        EvaluationResultService resultService = mock(EvaluationResultService.class);
        EvaluationCaseVersionService caseService = mock(EvaluationCaseVersionService.class);
        DeepAgentRunService deepRunService = mock(DeepAgentRunService.class);
        EvaluationSnapshotService snapshotService = mock(EvaluationSnapshotService.class);
        KnowledgeContextService knowledgeContextService = mock(KnowledgeContextService.class);
        AgentEvaluationTargetAdapter adapter = new AgentEvaluationTargetAdapter(agentService, resultService, caseService,
                deepRunService, snapshotService, mock(DeepAgentSigningClient.class),
                mock(AgentRunService.class), mock(StandardAgentEvaluationExecutor.class),
                mock(EvaluationResultCallbackService.class), mock(EvaluationExperimentService.class), knowledgeContextService);

        EvaluationTask task = new EvaluationTask();
        task.setPhase("EXECUTE"); task.setTargetType("AGENT"); task.setTargetId("agent-1");
        task.setResultId("result-1"); task.setSnapshotId("snapshot-1");
        EvaluationResult result = new EvaluationResult(); result.setId("result-1"); result.setCaseVersionId("case-1");
        EvaluationCaseVersion item = new EvaluationCaseVersion(); item.setId("case-1"); item.setInputJson("{\"message\":\"question\"}");
        EvaluationTargetSnapshot snapshot = new EvaluationTargetSnapshot();
        snapshot.setSnapshotJson("{\"executionMode\":\"DEEP\",\"knowledgeBases\":[{\"knowledgeBaseId\":\"kb-1\"}],\"tools\":[]}");
        AgentDefinition agent = new AgentDefinition(); agent.setId("agent-1"); agent.setExecutionMode("DEEP");
        List<Map<String, Object>> sources = List.of(Map.of("chunkId", "chunk-1", "content", "frozen context"));
        when(resultService.getById("result-1")).thenReturn(result);
        when(caseService.getById("case-1")).thenReturn(item);
        when(snapshotService.getById("snapshot-1")).thenReturn(snapshot);
        when(agentService.getById("agent-1")).thenReturn(agent);
        when(knowledgeContextService.enhance(any(), eq("evaluation"), eq(null), eq("agent-1"), eq("question"), eq(Set.of("kb-1")), eq("ENABLED"))).thenReturn(sources);
        when(deepRunService.startEvaluationRun(any(), eq("evaluation"), any(), eq("question"), any(), any(), any(), eq(Set.of("kb-1")), eq(sources))).thenReturn("run-1");

        adapter.dispatch(task);

        verify(knowledgeContextService).enhance(any(), eq("evaluation"), eq(null), eq("agent-1"), eq("question"), eq(Set.of("kb-1")), eq("ENABLED"));
        verify(deepRunService).startEvaluationRun(any(), eq("evaluation"), any(), eq("question"), any(), any(), any(), eq(Set.of("kb-1")), eq(sources));
        verify(resultService).updateById(result);
    }
}
