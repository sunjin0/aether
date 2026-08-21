package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextMetricServiceTest {
    @Mock
    private AgentRunContextMetricService metricStore;

    @Test
    void recordsSectionEstimatesAndImmutableFinalSnapshot() {
        ConversationContextService context = new ConversationContextService(null, null, null);
        ContextMetricService service = new ContextMetricService(context, metricStore);
        AgentDefinition agent = new AgentDefinition();
        agent.setModel("gpt-4o");
        agent.setMaxTokens(1000);
        ModelProvider provider = new ModelProvider();
        provider.setContextWindow(10000);
        List<ModelChatMessage> messages = Arrays.asList(
                new ModelChatMessage("system", "base system"),
                new ModelChatMessage("system", "【Skill test】instructions"),
                new ModelChatMessage("system", "【运行时上下文】retrieval"),
                new ModelChatMessage("system", "【对话历史摘要】summary"),
                new ModelChatMessage("assistant", "prior answer"),
                new ModelChatMessage("tool", "tool result"),
                new ModelChatMessage("user", "current request"));

        AgentRunContextMetric preliminary = service.recordPreliminary("run-1", 1, messages, agent, provider);
        AgentRunContextMetric finalMetric = service.recordFinal(preliminary, 321);

        assertEquals("PRELIMINARY", preliminary.getMetricPhase());
        assertEquals(context.getInputTokenBudget(agent, provider), preliminary.getInputBudgetTokens());
        assertTrue(preliminary.getSystemTokens() > 0);
        assertTrue(preliminary.getSkillTokens() > 0);
        assertTrue(preliminary.getRagTokens() > 0);
        assertTrue(preliminary.getSummaryTokens() > 0);
        assertTrue(preliminary.getHistoryTokens() > 0);
        assertTrue(preliminary.getToolTokens() > 0);
        assertTrue(preliminary.getCurrentMessageTokens() > 0);
        assertEquals(Integer.valueOf(0), preliminary.getToolDefinitionTokens());
        assertEquals("FINAL", finalMetric.getMetricPhase());
        assertEquals(preliminary.getModelCallId(), finalMetric.getSourceModelCallId());
        assertNotEquals(preliminary.getModelCallId(), finalMetric.getModelCallId());
        assertEquals(Integer.valueOf(321), finalMetric.getPromptTokens());
        ArgumentCaptor<AgentRunContextMetric> saved = ArgumentCaptor.forClass(AgentRunContextMetric.class);
        verify(metricStore, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals("PRELIMINARY", saved.getAllValues().get(0).getMetricPhase());
    }

    @Test
    void recordsToolDefinitionTokensWhenToolsAreSent() {
        ConversationContextService context = new ConversationContextService(null, null, null);
        ContextMetricService service = new ContextMetricService(context, metricStore);
        AgentDefinition agent = new AgentDefinition();
        agent.setModel("deepseek");
        agent.setMaxTokens(1000);
        ModelProvider provider = new ModelProvider();
        provider.setContextWindow(10000);
        AgentTool tool = new AgentTool();
        tool.setCode("query_knowledge");
        tool.setName("query_knowledge");
        tool.setDescription("检索知识库");
        tool.setParametersSchema("{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"}}}");
        List<ModelChatMessage> messages = Arrays.asList(
                new ModelChatMessage("user", "help"));

        AgentRunContextMetric preliminary = service.recordPreliminary("run-2", 1, messages,
                Arrays.asList(tool), agent, provider);

        assertTrue(preliminary.getToolDefinitionTokens() > 0);
        assertEquals(Integer.valueOf(0), preliminary.getToolTokens());
        assertTrue(preliminary.getToolDefinitionTokens() >= preliminary.getEstimatedPromptTokens());
    }

    @Test
    void ignoresToolsWithBlankCodeLikeTheWireSerializer() {
        ConversationContextService context = new ConversationContextService(null, null, null);
        ContextMetricService service = new ContextMetricService(context, metricStore);
        AgentDefinition agent = new AgentDefinition();
        agent.setModel("deepseek");
        agent.setMaxTokens(1000);
        ModelProvider provider = new ModelProvider();
        provider.setContextWindow(10000);
        AgentTool blankCodeTool = new AgentTool();
        blankCodeTool.setCode("");
        blankCodeTool.setName("hidden");
        blankCodeTool.setDescription("should not be counted");
        blankCodeTool.setParametersSchema("{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}");
        AgentTool realTool = new AgentTool();
        realTool.setCode("visible");
        realTool.setName("visible");
        realTool.setDescription("real tool");
        realTool.setParametersSchema("{\"type\":\"object\",\"properties\":{\"y\":{\"type\":\"string\"}}}");
        List<ModelChatMessage> messages = Arrays.asList(
                new ModelChatMessage("user", "help"));

        AgentRunContextMetric preliminary = service.recordPreliminary("run-3", 1, messages,
                Arrays.asList(blankCodeTool, realTool), agent, provider);

        assertTrue(preliminary.getToolDefinitionTokens() > 0);
        assertTrue(preliminary.getToolDefinitionTokens() < 100);
    }

    @Test
    void classifiesDeepTaskMemoryAndRetrievalSections() {
        ConversationContextService context = new ConversationContextService(null, null, null);
        ContextMetricService service = new ContextMetricService(context, metricStore);
        AgentDefinition agent = new AgentDefinition();
        agent.setModel("deepseek");
        List<ModelChatMessage> messages = Arrays.asList(
                new ModelChatMessage("system", "【会话记忆】\n- 已确认目标"),
                new ModelChatMessage("system", "【用户已确认偏好】\n- 表格输出"),
                new ModelChatMessage("system", "【当前Deep任务】{\"status\":\"RUNNING\"}"),
                new ModelChatMessage("system", "【运行时上下文】[{\"chunkId\":\"c1\"}]"),
                new ModelChatMessage("user", "继续分析"));

        AgentRunContextMetric preliminary = service.recordPreliminary("run-deep", 2,
                "DEEP_STEP", "NOT_NEEDED", messages, null, agent, null);

        assertEquals("DEEP_STEP", preliminary.getCallType());
        assertTrue(preliminary.getMemoryTokens() > 0);
        assertTrue(preliminary.getTaskTokens() > 0);
        assertTrue(preliminary.getRagTokens() > 0);
        assertTrue(preliminary.getCurrentMessageTokens() > 0);
    }

    @Test
    void recordsFinalFromLatestPreliminaryWithoutMutatingIt() {
        ConversationContextService context = new ConversationContextService(null, null, null);
        ContextMetricService service = new ContextMetricService(context, metricStore);
        AgentRunContextMetric preliminary = new AgentRunContextMetric();
        preliminary.setModelCallId("call-pre");
        preliminary.setRunId("run-deep");
        preliminary.setCallType("DEEP_STEP");
        preliminary.setAttemptNo(2);
        preliminary.setMetricPhase("PRELIMINARY");
        preliminary.setCompressionStatus("NOT_NEEDED");
        preliminary.setCompressedMessageCount(0);
        when(metricStore.list(any())).thenReturn(Collections.singletonList(preliminary));

        AgentRunContextMetric finalMetric = service.recordFinalForLatestPreliminary(
                "run-deep", "DEEP_STEP", 456, "NOT_NEEDED");

        assertEquals("FINAL", finalMetric.getMetricPhase());
        assertEquals("call-pre", finalMetric.getSourceModelCallId());
        assertNotEquals("call-pre", finalMetric.getModelCallId());
        assertEquals(Integer.valueOf(456), finalMetric.getPromptTokens());
        verify(metricStore).save(finalMetric);
    }
}
