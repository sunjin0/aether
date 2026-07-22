package com.aether.knowledge.service.impl;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelProviderService;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentIndexServiceImplTest {
    @Mock private KnowledgeDocumentService documentService;
    @Mock private KnowledgeDocumentChunkService chunkService;
    @Mock private KnowledgeBaseService baseService;
    @Mock private ModelProviderService providerService;
    @Mock private KnowledgeEmbeddingService embeddingService;
    @Mock private KnowledgeIndexJobService jobService;
    @Mock private KnowledgeIndexWorker indexWorker;
    @Mock private KnowledgeDocumentVersionService versionService;

    @Test
    void splitsEmbeddingRequestsIntoBatchesOfAtMostTen() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setEmbeddingProviderId("provider-1");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setContent(elevenSmallParagraphs());
        ModelProvider provider = new ModelProvider();
        provider.setId("provider-1");

        List<Integer> batchSizes = new ArrayList<>();
        when(baseService.getById("kb-1")).thenReturn(base);
        when(providerService.getById("provider-1")).thenReturn(provider);
        when(embeddingService.embedAll(eq(provider), anyList())).thenAnswer(invocation -> {
            List<String> inputs = invocation.getArgument(1);
            batchSizes.add(inputs.size());
            List<List<Double>> result = new ArrayList<>(inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                result.add(Collections.singletonList(1D));
            }
            return result;
        });
        when(embeddingService.toVectorLiteral(anyList())).thenReturn("[1]");
        when(chunkService.remove(any())).thenReturn(true);
        when(chunkService.saveVectorChunk(any())).thenReturn(true);

        KnowledgeDocumentIndexServiceImpl service = new KnowledgeDocumentIndexServiceImpl(
                documentService, chunkService, baseService, providerService, embeddingService,
                jobService, indexWorker, versionService, new KnowledgeChunkSplitter(10, 0));

        service.reindex(document, version);

        assertEquals(2, batchSizes.size());
        assertEquals(11, batchSizes.get(0) + batchSizes.get(1));
        assertTrue(batchSizes.get(0) <= 10);
        assertTrue(batchSizes.get(1) <= 10);
    }

    private String elevenSmallParagraphs() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                content.append("\n\n");
            }
            content.append("chunk").append(i);
        }
        return content.toString();
    }
}
