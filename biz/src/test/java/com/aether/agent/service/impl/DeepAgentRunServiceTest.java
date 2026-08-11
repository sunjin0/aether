package com.aether.agent.service.impl;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.*;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepAgentRunServiceTest {

    @Mock private DeepAgentSigningClient signingClient;
    @Mock private AgentRunService agentRunService;
    @Mock private AgentRunStepService agentRunStepService;
    @Mock private AgentConversationService agentConversationService;
    @Mock private AgentMessageService agentMessageService;
    @Mock private AgentToolCallLogService toolCallLogService;
    @Mock private DelegationTokenService delegationTokenService;
    @Mock private AgentToolCatalog toolCatalog;
    @Mock private KnowledgeContextService knowledgeContextService;
    @Mock private SkillArtifactExecutionService artifactExecutionService;
    @Mock private DeepAgentConfig config;

    private DeepAgentRunService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentRun.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentConversation.class);
        service = new DeepAgentRunService(agentRunService, agentRunStepService,
                signingClient, agentConversationService, agentMessageService,
                toolCallLogService,
                delegationTokenService, toolCatalog, knowledgeContextService, artifactExecutionService, config);
    }

    @Test
    void startRunCreatesLocalRunAndCallsExternalService() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1"); agent.setSystemPrompt("你是助手");
        agent.setMaxToolRounds(5); agent.setModel("deepseek-v4");

        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            run.setId("run-1");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(eq("run-1"), eq("user-1"), eq("agent-1"), anyList(), anyList()))
                .thenReturn("delegation-jwt");
        when(toolCatalog.getBoundTools("agent-1")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body("{\"run_id\":\"run-1\",\"status\":\"QUEUED\",\"created\":true}"));

        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        when(agentConversationService.getById("conversation-1")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            AgentMessage msg = inv.getArgument(0);
            msg.setId("message-1");
            return true;
        });

        String runId = service.startRun(agent, "user-1", "conversation-1", "你好", Collections.emptyList());

        assertEquals("run-1", runId);
        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals(3, runCaptor.getValue().getStatus());
        assertEquals("DEEP", runCaptor.getValue().getExecutionMode());
        assertEquals("你好", runCaptor.getValue().getInputContent());
        assertEquals("run-1", runCaptor.getValue().getExternalRunId());
        assertEquals("[]", runCaptor.getValue().getRetrievalSources());
        verify(agentConversationService).update(isNull(), any());
    }

    @Test
    void keepsSimilarityInKnowledgeSourcesSentToDeepAgent() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentName", "运维手册");
        source.put("documentId", "document-1");
        source.put("chunkId", "chunk-1");
        source.put("content", "知识库内容");
        source.put("citationIndex", 1);
        source.put("similarity", 0.87D);
        source.put("retrievalScore", 0.91D);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service, "buildKnowledgeSources", Collections.singletonList(source));

        assertEquals(0.87D, result.get(0).get("similarity"));
        assertEquals(0.91D, result.get(0).get("retrievalScore"));
    }

    @Test
    void startRunKeepsExistingConversationTitle() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-title");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-title");
        conversation.setTitle("已有标题");
        when(agentConversationService.getById("conversation-title")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentMessage.class).setId("message-title"); return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentRun.class).setId("run-title"); return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(eq("run-title"), anyString(), anyString(), anyList(), anyList())).thenReturn("token");
        when(toolCatalog.getBoundTools("agent-title")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        service.startRun(agent, "user-title", "conversation-title", "新的任务标题", Collections.emptyList());

        verify(agentConversationService, never()).update(isNull(), any());
    }

    @Test
    void startRunPreservesAttachmentMetadataAndPassesItToDeepTask() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-attachment");
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-attachment");
        when(agentConversationService.getById("conversation-attachment")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentMessage.class).setId("message-attachment"); return true;
        });
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentRun.class).setId("run-attachment"); return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(eq("run-attachment"), anyString(), anyString(), anyList(), anyList())).thenReturn("token");
        when(toolCatalog.getBoundTools("agent-attachment")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{}"));

        service.startRun(agent, "user-attachment", "conversation-attachment", "请总结附件", "附件正文", "[{\"fileName\":\"a.txt\"}]", Collections.emptyList(), null);

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageService).save(messageCaptor.capture());
        assertEquals("请总结附件", messageCaptor.getValue().getContent());
        assertEquals("附件正文", messageCaptor.getValue().getAttachmentContent());
        ArgumentCaptor<Map> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(signingClient).signedPost(eq("/v1/runs"), requestCaptor.capture());
        assertEquals("请总结附件\n\n附件内容：\n附件正文", requestCaptor.getValue().get("task"));
    }

    @Test
    void startRunFailsWhenExternalReturnsNon202() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-2"); agent.setSystemPrompt("test");

        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            run.setId("run-2");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(delegationTokenService.create(anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn("delegation-jwt");
        when(toolCatalog.getBoundTools("agent-2")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error"));

        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-2");
        when(agentConversationService.getById("conversation-2")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            AgentMessage msg = inv.getArgument(0);
            msg.setId("message-2");
            return true;
        });

        assertThrows(RuntimeException.class, () ->
                service.startRun(agent, "user-2", "conversation-2", "hi", Collections.emptyList()));

        verify(agentRunService).update(isNull(), any());
    }

    @Test
    void handleCallbackSavesStepOnceAndIgnoresDuplicate() {
        when(agentRunService.getById("run-1")).thenReturn(deepRun("run-1", "conversation-1", 3));
        when(agentRunStepService.saveIfAbsent(any(AgentRunStep.class)))
                .thenReturn(true).thenReturn(false);

        boolean first = service.handleCallback("run-1", "evt-1", "tool.started", 1000L, "{\"tool\":\"x\"}");
        boolean second = service.handleCallback("run-1", "evt-1", "tool.started", 1000L, "{\"tool\":\"x\"}");

        assertTrue(first);
        assertFalse(second);
        verify(agentRunStepService, times(2)).saveIfAbsent(any(AgentRunStep.class));
    }

    @Test
    void handleToolStartedCallbackCreatesPendingPlatformAudit() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        run.setAgentDefinitionId("agent-1");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunStepService.saveIfAbsent(any(AgentRunStep.class))).thenReturn(true);
        AgentTool tool = new AgentTool();
        tool.setId("tool-1");
        tool.setMcpToolName("get_current_time");
        when(toolCatalog.getBoundTools(anyString())).thenReturn(Collections.singletonList(tool));

        assertTrue(service.handleCallback("run-1", "evt-tool", "tool.started", 1000L,
                "{\"toolCallId\":\"call-1\",\"toolName\":\"get_current_time\",\"arguments\":\"{ }\"}"));

        ArgumentCaptor<AgentToolCallLog> audit = ArgumentCaptor.forClass(AgentToolCallLog.class);
        verify(toolCallLogService).save(audit.capture());
        assertEquals("run-1", audit.getValue().getRunId());
        assertEquals("call-1", audit.getValue().getToolCallId());
        assertEquals("get_current_time", audit.getValue().getToolName());
        assertEquals("tool-1", audit.getValue().getToolId());
        assertEquals(4, audit.getValue().getStatus());
    }

    @Test
    void handleCallbackRejectsUnknownRunBeforeSavingStep() {
        when(agentRunService.getById("unknown-run")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                service.handleCallback("unknown-run", "evt-1", "run.started", 1000L, "{}"));

        verify(agentRunStepService, never()).saveIfAbsent(any(AgentRunStep.class));
    }

    @Test
    void startRunRegistersCallbackBeforeDispatch() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-3");
        java.util.function.Consumer<String> registration = mock(java.util.function.Consumer.class);
        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentRun.class).setId("run-3");
            return true;
        });
        when(agentRunService.updateById(any(AgentRun.class))).thenReturn(true);
        when(agentConversationService.getById("conversation-3")).thenReturn(new AgentConversation());
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentMessage.class).setId("message-3");
            return true;
        });
        when(delegationTokenService.create(eq("run-3"), anyString(), anyString(), anyList(), anyList())).thenReturn("token");
        when(toolCatalog.getBoundTools("agent-3")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap())).thenAnswer(inv -> {
            verify(registration).accept("run-3");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("{}");
        });

        service.startRun(agent, "user-3", "conversation-3", "task", Collections.emptyList(), registration);

        InOrder order = inOrder(registration, signingClient);
        order.verify(registration).accept("run-3");
        order.verify(signingClient).signedPost(eq("/v1/runs"), anyMap());
    }

    @Test
    void markSucceededReturnsFalseWhenRunIsAlreadyTerminal() {
        when(agentRunService.update(isNull(), any())).thenReturn(false);

        boolean transitioned = service.markSucceeded("run-1", "final answer", "deep-model", 12, 8, 20);

        assertFalse(transitioned);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(agentRunService).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("status"));
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("IN"));
    }

    @Test
    void completeRunDoesNotSaveAssistantMessageWhenActiveStatusClaimFails() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(false);

        DeepAgentRunService.CompletedRun completed = service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null);

        assertNull(completed);
        verify(agentMessageService, never()).save(any(AgentMessage.class));
        verify(agentConversationService, never()).update(isNull(), any());
    }

    @Test
    void completeRunFailsWhenAssistantMessageCannotBePersisted() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true);
        when(agentMessageService.save(any(AgentMessage.class))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null));
        verify(agentConversationService, never()).update(isNull(), any());
    }

    @Test
    void completeRunAttachesPersistedMessageAfterClaimingSuccess() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });

        DeepAgentRunService.CompletedRun completed = service.completeRun("run-1", "final answer", "deep-model",
                12, 8, 20, null, null, null, null);

        assertNotNull(completed);
        assertEquals("conversation-1", completed.getConversationId());
        assertEquals("message-1", completed.getMessageId());
        verify(agentRunService, times(2)).update(isNull(), any());
        verify(agentConversationService).update(isNull(), any());
    }

    @Test
    void completeRunRecordsCitationsAndRetrievalOutcomeFromPersistedSources() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        run.setAgentDefinitionId("agent-1");
        run.setInputContent("original task");
        run.setRetrievalSources("[{\"citationIndex\":1,\"chunkId\":\"chunk-1\",\"knowledgeBaseId\":\"kb-1\",\"documentId\":\"doc-1\"}]");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });
        List<Map<String, Object>> cited = Collections.singletonList(source("chunk-1"));
        when(knowledgeContextService.ensureCitations(any(com.aether.agent.model.ModelStreamResponse.class), anyList()))
                .thenReturn(cited);

        service.completeRun("run-1", "final answer【1】", "deep-model", 12, 8, 20,
                null, null, null, null);

        ArgumentCaptor<List<Map<String, Object>>> citedCaptor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeContextService).recordCitations(eq("agent-1"), eq("conversation-1"), eq("message-1"), citedCaptor.capture());
        assertEquals("chunk-1", citedCaptor.getValue().get(0).get("chunkId"));
        verify(knowledgeContextService).recordRetrievalOutcome(eq("agent-1"), eq("conversation-1"), eq("message-1"),
                eq("original task"), anyList(), eq(citedCaptor.getValue()));
    }

    @Test
    void completeRunMalformedSourcesRecordsEmptyOutcomeAndStaleCompletionDoesNotAudit() {
        AgentRun run = deepRun("run-1", "conversation-1", 4);
        run.setAgentDefinitionId("agent-1");
        run.setInputContent("original task");
        run.setRetrievalSources("not-json");
        when(agentRunService.getById("run-1")).thenReturn(run);
        when(agentRunService.update(isNull(), any())).thenReturn(true, true);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentMessage.class).setId("message-1");
            return true;
        });

        service.completeRun("run-1", "final answer", "deep-model", 12, 8, 20,
                null, null, null, null);

        verify(knowledgeContextService).recordRetrievalOutcome(eq("agent-1"), eq("conversation-1"), eq("message-1"),
                eq("original task"), eq(Collections.emptyList()), eq(Collections.emptyList()));
        reset(knowledgeContextService);
        when(agentRunService.update(isNull(), any())).thenReturn(false);

        assertNull(service.completeRun("run-1", "late answer", "deep-model", null, null, null,
                null, null, null, null));
        verifyNoInteractions(knowledgeContextService);
    }

    private AgentRun deepRun(String runId, String conversationId, int status) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setConversationId(conversationId);
        run.setExecutionMode("DEEP");
        run.setStatus(status);
        run.setDeleted(false);
        return run;
    }

    private Map<String, Object> source(String chunkId) {
        Map<String, Object> source = new HashMap<>();
        source.put("chunkId", chunkId);
        return source;
    }
}
