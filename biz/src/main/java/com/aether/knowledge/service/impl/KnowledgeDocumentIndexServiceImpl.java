package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.agent.service.ModelProviderService;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeDocumentIndexServiceImpl implements KnowledgeDocumentIndexService {

    private static final int DOCUMENT_STATUS_INDEXING = 1;
    private static final int DOCUMENT_STATUS_DONE = 2;
    private static final int KB_INDEX_STATUS_INDEXING = 1;
    private static final int KB_INDEX_STATUS_DONE = 2;
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    public KnowledgeDocumentIndexServiceImpl(KnowledgeDocumentService knowledgeDocumentService,
                                         KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                         KnowledgeBaseService knowledgeBaseService,
                                         ModelProviderService modelProviderService,
                                         KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.modelProviderService = modelProviderService;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(KnowledgeDocument document) {
        if (document == null || StringUtils.isBlank(document.getId())) {
            throw new ServerException(400, "document is required");
        }
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(document.getKnowledgeBaseId());
        if (knowledgeBase == null || Boolean.TRUE.equals(knowledgeBase.getDeleted())) {
            throw new ServerException(404, "knowledge base not found");
        }
        ModelProvider provider = getEmbeddingProvider(knowledgeBase);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())) {
            throw new ServerException(404, "model provider not found");
        }

        updateDocumentStatus(document.getId(), DOCUMENT_STATUS_INDEXING, null);
        updateKnowledgeBaseIndexStatus(knowledgeBase.getId(), KB_INDEX_STATUS_INDEXING);
        knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                .eq(KnowledgeDocumentChunk::getDocumentId, document.getId()));

        List<String> chunks = split(document.getContent());
        int index = 0;
        for (String chunkText : chunks) {
            String vector = knowledgeEmbeddingService.toVectorLiteral(knowledgeEmbeddingService.embed(provider, chunkText));
            KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
            chunk.setKnowledgeBaseId(knowledgeBase.getId());
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkText);
            chunk.setTokenCount(estimateTokens(chunkText));
            chunk.setEmbedding(vector);
            knowledgeDocumentChunkService.saveVectorChunk(chunk);
        }
        updateDocumentStatus(document.getId(), DOCUMENT_STATUS_DONE, chunks.size());
        updateKnowledgeBaseIndexStatus(knowledgeBase.getId(), KB_INDEX_STATUS_DONE);
    }

    private List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        if (StringUtils.isBlank(content)) {
            return chunks;
        }
        String normalized = content.trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private int estimateTokens(String text) {
        return StringUtils.isBlank(text) ? 0 : Math.max(1, text.length() / 4);
    }

    private void updateDocumentStatus(String documentId, Integer status, Integer chunkCount) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(status);
        if (chunkCount != null) {
            update.setChunkCount(chunkCount);
        }
        knowledgeDocumentService.updateById(update);
    }

    private void updateKnowledgeBaseIndexStatus(String knowledgeBaseId, Integer indexStatus) {
        KnowledgeBase update = new KnowledgeBase();
        update.setId(knowledgeBaseId);
        update.setIndexStatus(indexStatus);
        knowledgeBaseService.updateById(update);
    }

    private ModelProvider getEmbeddingProvider(KnowledgeBase knowledgeBase) {
        if (StringUtils.isNotBlank(knowledgeBase.getEmbeddingProviderId())) {
            return modelProviderService.getById(knowledgeBase.getEmbeddingProviderId());
        }
        List<ModelProvider> providers = modelProviderService.list(Wrappers.lambdaQuery(ModelProvider.class)
                .eq(ModelProvider::getStatus, 1)
                .eq(ModelProvider::getDeleted, false)
                .orderByAsc(ModelProvider::getSortNum));
        return providers == null || providers.isEmpty() ? null : providers.get(0);
    }
}
