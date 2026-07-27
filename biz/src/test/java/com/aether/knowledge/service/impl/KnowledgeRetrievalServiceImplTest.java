package com.aether.knowledge.service.impl;

import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.agent.service.ModelProviderService;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.knowledge.service.KnowledgeRerankService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceImplTest {
    @Mock private KnowledgeBaseService baseService;
    @Mock private AgentKnowledgeBaseBindingService bindingService;
    @Mock private KnowledgeDocumentChunkService chunkService;
    @Mock private ModelProviderService providerService;
    @Mock private KnowledgeEmbeddingService embeddingService;
    @Mock private KnowledgeRerankService rerankService;

    @Test
    void filtersCandidatesBelowConfiguredSimilarity() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-1");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("provider-1");
        base.setId("kb-1");
        base.setRetrievalConfig("{\"topK\":3,\"minSimilarity\":0.7}");
        ModelProvider provider = new ModelProvider().setStatus(1);
        provider.setId("provider-1");
        KnowledgeDocumentChunk relevant = chunk("relevant", 0.85D);
        KnowledgeDocumentChunk irrelevant = chunk("irrelevant", 0.20D);

        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("provider-1")).thenReturn(provider);
        when(embeddingService.embed(provider, "query")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(irrelevant, relevant));

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService);
        KnowledgeRetrievalResult result = service.retrieve("agent-1", "query");

        assertEquals(1, result.getChunks().size());
        assertEquals("relevant", result.getChunks().get(0).getContent());
    }

    @Test
    void appliesPerDocumentLimitAndRemovesDuplicateContent() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-1");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("provider-1");
        base.setId("kb-1");
        base.setRetrievalConfig("{\"topK\":3,\"minSimilarity\":0.7,\"maxChunksPerDocument\":1}");
        ModelProvider provider = new ModelProvider().setStatus(1);
        provider.setId("provider-1");

        KnowledgeDocumentChunk first = chunk("first", 0.95D);
        first.setId("chunk-1"); first.setDocumentId("doc-1"); first.setContentHash("hash-1");
        KnowledgeDocumentChunk sameDocument = chunk("second", 0.90D);
        sameDocument.setId("chunk-2"); sameDocument.setDocumentId("doc-1"); sameDocument.setContentHash("hash-2");
        KnowledgeDocumentChunk duplicate = chunk("first copy", 0.85D);
        duplicate.setId("chunk-3"); duplicate.setDocumentId("doc-2"); duplicate.setContentHash("hash-1");
        KnowledgeDocumentChunk anotherDocument = chunk("third", 0.80D);
        anotherDocument.setId("chunk-4"); anotherDocument.setDocumentId("doc-3"); anotherDocument.setContentHash("hash-3");

        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("provider-1")).thenReturn(provider);
        when(embeddingService.embed(provider, "query")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(first, sameDocument, duplicate, anotherDocument));

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService);
        KnowledgeRetrievalResult result = service.retrieve("agent-1", "query");

        assertEquals(2, result.getChunks().size());
        assertEquals("first", result.getChunks().get(0).getContent());
        assertEquals("third", result.getChunks().get(1).getContent());
    }

    @Test
    void keepsHighestScoringChunkWhenRetrievedContextExceedsTokenBudget() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-1");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("provider-1");
        base.setId("kb-1");
        ModelProvider provider = new ModelProvider().setStatus(1);
        provider.setId("provider-1");
        KnowledgeDocumentChunk first = chunk("highest", 0.95D);
        first.setId("chunk-1"); first.setDocumentId("doc-1"); first.setTokenCount(8000);
        KnowledgeDocumentChunk second = chunk("next", 0.90D);
        second.setId("chunk-2"); second.setDocumentId("doc-2"); second.setTokenCount(5000);

        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("provider-1")).thenReturn(provider);
        when(embeddingService.embed(provider, "query")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(first, second));

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService);
        KnowledgeRetrievalResult result = service.retrieve("agent-1", "query");

        assertEquals(1, result.getChunks().size());
        assertEquals("highest", result.getChunks().get(0).getContent());
    }

    @Test
    void cachesEmbeddingForRepeatedNormalizedQuery() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-cache");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("provider-cache");
        base.setId("kb-cache");
        ModelProvider provider = new ModelProvider().setStatus(1);
        provider.setId("provider-cache"); provider.setDefaultModel("embedding-model");
        KnowledgeDocumentChunk candidate = chunk("cached", 0.9D);
        candidate.setId("chunk-cache"); candidate.setDocumentId("doc-cache");

        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("provider-cache")).thenReturn(provider);
        when(embeddingService.embed(provider, "repeat query")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(candidate));

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService);
        service.retrieve("agent-cache", "repeat query");
        service.retrieve("agent-cache", "repeat   query");

        verify(embeddingService, times(1)).embed(provider, "repeat query");
        verify(chunkService, times(1)).searchSimilarChunks(anyList(), anyString(), anyInt());
    }

    @Test
    void returnsLexicalOnlyCandidateThroughHybridRetrieval() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-hybrid");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("provider-hybrid");
        base.setId("kb-hybrid");
        base.setRetrievalConfig("{\"minSimilarity\":0.8,\"hybridEnabled\":true,\"minLexicalScore\":0.05}");
        ModelProvider provider = new ModelProvider().setStatus(1);
        provider.setId("provider-hybrid");
        KnowledgeDocumentChunk semantic = chunk("semantic", 0.4D);
        semantic.setId("semantic-1"); semantic.setDocumentId("doc-semantic");
        KnowledgeDocumentChunk lexical = new KnowledgeDocumentChunk();
        lexical.setId("lexical-1"); lexical.setDocumentId("doc-lexical"); lexical.setContent("SKU-2026-A");
        lexical.setLexicalScore(0.8D);

        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("provider-hybrid")).thenReturn(provider);
        when(embeddingService.embed(provider, "SKU-2026-A")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt())).thenReturn(Collections.singletonList(semantic));
        when(chunkService.searchLexicalChunks(anyList(), anyString(), anyInt())).thenReturn(Collections.singletonList(lexical));

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService);
        KnowledgeRetrievalResult result = service.retrieve("agent-hybrid", "SKU-2026-A");

        assertEquals(1, result.getChunks().size());
        assertEquals("lexical-1", result.getChunks().get(0).getId());
    }

    @Test
    void usesConfiguredRerankerBeforeSelectingFinalChunks() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-rerank");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("embedding-provider");
        base.setId("kb-rerank");
        base.setRetrievalConfig("{\"topK\":1,\"rerankEnabled\":true,\"rerankProviderId\":\"rerank-provider\",\"rerankTopN\":1}");
        ModelProvider embeddingProvider = new ModelProvider().setStatus(1);
        embeddingProvider.setId("embedding-provider");
        ModelProvider rerankProvider = new ModelProvider().setStatus(1);
        rerankProvider.setId("rerank-provider");
        KnowledgeDocumentChunk first = chunk("first", 0.95D);
        first.setId("first"); first.setDocumentId("doc-1");
        KnowledgeDocumentChunk preferred = chunk("preferred", 0.8D);
        preferred.setId("preferred"); preferred.setDocumentId("doc-2");

        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("embedding-provider")).thenReturn(embeddingProvider);
        when(providerService.getById("rerank-provider")).thenReturn(rerankProvider);
        when(embeddingService.embed(embeddingProvider, "question")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt())).thenReturn(Arrays.asList(first, preferred));
        when(rerankService.rerank(eq(rerankProvider), nullable(String.class), eq("question"), anyList(), eq(1)))
                .thenReturn(Collections.singletonList(preferred));

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService, rerankService);
        KnowledgeRetrievalResult result = service.retrieve("agent-rerank", "question");

        assertEquals("preferred", result.getChunks().get(0).getId());
        verify(rerankService).rerank(eq(rerankProvider), nullable(String.class), eq("question"), anyList(), eq(1));
    }

    @Test
    void expandsIdentifierTermsForLexicalRetrieval() {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setKnowledgeBaseId("kb-expand");
        KnowledgeBase base = new KnowledgeBase().setEmbeddingProviderId("provider-expand");
        base.setId("kb-expand");
        ModelProvider provider = new ModelProvider().setStatus(1);
        provider.setId("provider-expand");
        when(bindingService.list(any())).thenReturn(Collections.singletonList(binding));
        when(baseService.list(any())).thenReturn(Collections.emptyList(), Collections.singletonList(base));
        when(providerService.getById("provider-expand")).thenReturn(provider);
        when(embeddingService.embed(provider, "查询 SKU-2026-A 的状态")).thenReturn(Collections.singletonList(1D));
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.searchSimilarChunks(anyList(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(chunkService.searchLexicalChunks(anyList(), anyString(), anyInt())).thenReturn(Collections.emptyList());

        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                baseService, bindingService, chunkService, providerService, embeddingService);
        service.retrieve("agent-expand", "查询 SKU-2026-A 的状态");

        verify(chunkService).searchLexicalChunks(anyList(), eq("SKU-2026-A"), anyInt());
    }

    private KnowledgeDocumentChunk chunk(String content, double similarity) {
        KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
        chunk.setKnowledgeBaseId("kb-1");
        chunk.setContent(content);
        chunk.setSimilarity(similarity);
        return chunk;
    }
}
