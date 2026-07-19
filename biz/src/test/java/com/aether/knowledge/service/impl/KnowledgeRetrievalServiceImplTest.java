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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceImplTest {
    @Mock private KnowledgeBaseService baseService;
    @Mock private AgentKnowledgeBaseBindingService bindingService;
    @Mock private KnowledgeDocumentChunkService chunkService;
    @Mock private ModelProviderService providerService;
    @Mock private KnowledgeEmbeddingService embeddingService;

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

    private KnowledgeDocumentChunk chunk(String content, double similarity) {
        KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
        chunk.setKnowledgeBaseId("kb-1");
        chunk.setContent(content);
        chunk.setSimilarity(similarity);
        return chunk;
    }
}
