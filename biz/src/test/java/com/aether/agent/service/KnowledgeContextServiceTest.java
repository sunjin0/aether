package com.aether.agent.service;

import com.aether.knowledge.mapper.KnowledgeReferenceLogMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalLogMapper;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证知识库Context服务的行为。
 */
class KnowledgeContextServiceTest {

    /**
     * 处理persistsOnlyCompleteCitedSources。
     */
    @Test
    void persistsOnlyCompleteCitedSources() {
        KnowledgeReferenceLogMapper mapper = mock(KnowledgeReferenceLogMapper.class);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), mock(KnowledgeRetrievalService.class),
                mock(KnowledgeDocumentService.class), mapper);
        Map<String, Object> source = new HashMap<>();
        source.put("knowledgeBaseId", "kb-1");
        source.put("documentId", "doc-1");
        source.put("documentVersionId", "version-1");
        source.put("chunkId", "chunk-1");
        source.put("similarity", 0.91D);
        source.put("citationIndex", 2);
        when(mapper.insert(any(com.aether.knowledge.entity.KnowledgeReferenceLog.class))).thenReturn(1);

        service.recordCitations("agent-1", "conversation-1", "message-1",
                Collections.singletonList(source));

        org.mockito.ArgumentCaptor<com.aether.knowledge.entity.KnowledgeReferenceLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.aether.knowledge.entity.KnowledgeReferenceLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("chunk-1", captor.getValue().getChunkId());
        assertEquals(2, captor.getValue().getCitationNo().intValue());
        assertEquals(0.91D, captor.getValue().getSimilarity());
        verify(mapper).incrementChunkReference("chunk-1", captor.getValue().getReferencedAt());
        verify(mapper).incrementDocumentReference("doc-1", captor.getValue().getReferencedAt());
        verify(mapper).incrementKnowledgeBaseReference("kb-1", captor.getValue().getReferencedAt());
    }

    /**
     * 处理addsGroundingWarningWhen知识库Retrieval判断是否拥有NoMatch。
     */
    @Test
    void addsGroundingWarningWhenKnowledgeRetrievalHasNoMatch() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        result.setRetrievalAttempted(true);
        when(retrievalService.retrieveWithHistory(org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.eq("question"), org.mockito.ArgumentMatchers.anyList())).thenReturn(result);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), retrievalService,
                mock(KnowledgeDocumentService.class), mock(KnowledgeReferenceLogMapper.class));
        ArrayList<ModelChatMessage> context = new ArrayList<>();

        service.enhance(context, "user-1", "conversation-1", "agent-1", "question");

        assertEquals(1, context.size());
        org.junit.jupiter.api.Assertions.assertTrue(context.get(0).getContent().contains("未检索到"));
    }

    /**
     * 处理usesStrictGroundingWarningWhenConfigured。
     */
    @Test
    void usesStrictGroundingWarningWhenConfigured() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        result.setRetrievalAttempted(true);
        result.setStrictGrounding(true);
        when(retrievalService.retrieveWithHistory(org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.eq("question"), org.mockito.ArgumentMatchers.anyList())).thenReturn(result);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), retrievalService, mock(KnowledgeDocumentService.class));
        ArrayList<ModelChatMessage> context = new ArrayList<>();

        service.enhance(context, "user-1", "conversation-1", "agent-1", "question");

        org.junit.jupiter.api.Assertions.assertTrue(context.get(0).getContent().contains("只能基于知识库资料回答"));
    }

    /**
     * 处理combinesRuntimeSectionsIntoOneOrderedSystemPrompt。
     */
    @Test
    void combinesRuntimeSectionsIntoOneOrderedSystemPrompt() {
        AdminPreferenceService preferences = mock(AdminPreferenceService.class);
        when(preferences.buildPreferenceContext("user-1", null, "conversation-1"))
                .thenReturn("【表达偏好】使用中文");
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        result.setContext("【知识库检索结果】\n片段 1：续费健康检查");
        final boolean[] retrievalReceivedPreference = new boolean[1];
        when(retrievalService.retrieveWithHistory(org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.eq("question"), org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> {
            java.util.List<ModelChatMessage> retrievalContext = invocation.getArgument(2);
            retrievalReceivedPreference[0] = retrievalContext.toString().contains("【表达偏好】");
            return result;
        });
        KnowledgeContextService service = new KnowledgeContextService(preferences, retrievalService,
                mock(KnowledgeDocumentService.class));
        ArrayList<ModelChatMessage> context = new ArrayList<>();
        context.add(new ModelChatMessage("system", "基础规则"));

        service.enhance(context, "user-1", "conversation-1", "agent-1", "question");

        assertEquals(2, context.size());
        assertEquals("基础规则", context.get(0).getContent());
        String prompt = context.get(1).getContent();
        assertTrue(prompt.startsWith("【运行时上下文】"));
        assertTrue(prompt.indexOf("【表达偏好】") < prompt.indexOf("【知识库检索结果】"));
        verify(retrievalService).retrieveWithHistory(org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.eq("question"), org.mockito.ArgumentMatchers.anyList());
        org.junit.jupiter.api.Assertions.assertFalse(retrievalReceivedPreference[0]);
    }

    /**
     * 处理skipsRetrieval用于CasualUnscopedTurn。
     */
    @Test
    void skipsRetrievalForCasualUnscopedTurn() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        KnowledgeContextService service = new KnowledgeContextService(mock(AdminPreferenceService.class), retrievalService,
                mock(KnowledgeDocumentService.class));

        service.enhance(new ArrayList<ModelChatMessage>(), "user-1", "conversation-1", "agent-1", "你好", null);

        verify(retrievalService, org.mockito.Mockito.never()).retrieve(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anySet());
    }

    /**
     * 处理recordsWhetherRetrievedChunkWasCitedWithoutPersistingRaw查询。
     */
    @Test
    void recordsWhetherRetrievedChunkWasCitedWithoutPersistingRawQuery() {
        KnowledgeRetrievalLogMapper mapper = mock(KnowledgeRetrievalLogMapper.class);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), mock(KnowledgeRetrievalService.class),
                mock(KnowledgeDocumentService.class), mock(KnowledgeReferenceLogMapper.class), mapper);
        Map<String, Object> source = new HashMap<>();
        source.put("knowledgeBaseId", "kb-1");
        source.put("documentId", "doc-1");
        source.put("chunkId", "chunk-1");
        source.put("similarity", 0.8D);
        source.put("retrievalScore", 0.9D);

        service.recordRetrievalOutcome("agent-1", "conversation-1", "message-1", "private question",
                Collections.singletonList(source), Collections.singletonList(source));

        org.mockito.ArgumentCaptor<com.aether.knowledge.entity.KnowledgeRetrievalLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.aether.knowledge.entity.KnowledgeRetrievalLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("MATCHED", captor.getValue().getOutcome());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getCited());
        org.junit.jupiter.api.Assertions.assertFalse("private question".equals(captor.getValue().getQueryHash()));
    }

    /**
     * 处理removesCitationIndexesThatWereNotProvided按Retrieval。
     */
    @Test
    void removesCitationIndexesThatWereNotProvidedByRetrieval() {
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), mock(KnowledgeRetrievalService.class),
                mock(KnowledgeDocumentService.class));
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("已验证【1】，不存在的引用【10】必须移除。");
        Map<String, Object> source = new HashMap<>();
        source.put("citationIndex", 1);

        assertEquals(1, service.ensureCitations(response, Collections.singletonList(source)).size());
        assertEquals("已验证【1】，不存在的引用必须移除。", response.getContent());
    }
}
