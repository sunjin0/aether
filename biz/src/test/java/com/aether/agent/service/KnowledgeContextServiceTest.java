package com.aether.agent.service;

import com.aether.knowledge.mapper.KnowledgeReferenceLogMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalLogMapper;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.agent.model.ModelChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeContextServiceTest {

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

    @Test
    void addsGroundingWarningWhenKnowledgeRetrievalHasNoMatch() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        result.setRetrievalAttempted(true);
        when(retrievalService.retrieve("agent-1", "question")).thenReturn(result);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), retrievalService,
                mock(KnowledgeDocumentService.class), mock(KnowledgeReferenceLogMapper.class));
        ArrayList<ModelChatMessage> context = new ArrayList<>();

        service.enhance(context, "user-1", "conversation-1", "agent-1", "question");

        assertEquals(1, context.size());
        org.junit.jupiter.api.Assertions.assertTrue(context.get(0).getContent().contains("未检索到"));
    }

    @Test
    void usesStrictGroundingWarningWhenConfigured() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        result.setRetrievalAttempted(true); result.setStrictGrounding(true);
        when(retrievalService.retrieve("agent-1", "question")).thenReturn(result);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), retrievalService, mock(KnowledgeDocumentService.class));
        ArrayList<ModelChatMessage> context = new ArrayList<>();

        service.enhance(context, "user-1", "conversation-1", "agent-1", "question");

        org.junit.jupiter.api.Assertions.assertTrue(context.get(0).getContent().contains("只能基于知识库资料回答"));
    }

    @Test
    void recordsWhetherRetrievedChunkWasCitedWithoutPersistingRawQuery() {
        KnowledgeRetrievalLogMapper mapper = mock(KnowledgeRetrievalLogMapper.class);
        KnowledgeContextService service = new KnowledgeContextService(
                mock(AdminPreferenceService.class), mock(KnowledgeRetrievalService.class),
                mock(KnowledgeDocumentService.class), mock(KnowledgeReferenceLogMapper.class), mapper);
        Map<String, Object> source = new HashMap<>();
        source.put("knowledgeBaseId", "kb-1"); source.put("documentId", "doc-1"); source.put("chunkId", "chunk-1");
        source.put("similarity", 0.8D); source.put("retrievalScore", 0.9D);

        service.recordRetrievalOutcome("agent-1", "conversation-1", "message-1", "private question",
                Collections.singletonList(source), Collections.singletonList(source));

        org.mockito.ArgumentCaptor<com.aether.knowledge.entity.KnowledgeRetrievalLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.aether.knowledge.entity.KnowledgeRetrievalLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("MATCHED", captor.getValue().getOutcome());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getCited());
        org.junit.jupiter.api.Assertions.assertFalse("private question".equals(captor.getValue().getQueryHash()));
    }
}
