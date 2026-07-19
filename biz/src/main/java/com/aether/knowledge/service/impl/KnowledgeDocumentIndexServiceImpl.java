package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.agent.service.ModelProviderService;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class KnowledgeDocumentIndexServiceImpl implements KnowledgeDocumentIndexService {

    private static final int DOCUMENT_STATUS_INDEXING = 1;
    private static final int DOCUMENT_STATUS_DONE = 2;
    private static final int KB_INDEX_STATUS_DONE = 2;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeIndexJobService knowledgeIndexJobService;
    private final KnowledgeIndexWorker knowledgeIndexWorker;
    private final KnowledgeDocumentVersionService knowledgeDocumentVersionService;
    private final KnowledgeChunkSplitter chunkSplitter;

    public KnowledgeDocumentIndexServiceImpl(KnowledgeDocumentService knowledgeDocumentService,
                                         KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                         KnowledgeBaseService knowledgeBaseService,
                                         ModelProviderService modelProviderService,
                                         KnowledgeEmbeddingService knowledgeEmbeddingService,
                                         KnowledgeIndexJobService knowledgeIndexJobService,
                                         KnowledgeIndexWorker knowledgeIndexWorker,
                                         KnowledgeDocumentVersionService knowledgeDocumentVersionService,
                                         KnowledgeChunkSplitter chunkSplitter) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.modelProviderService = modelProviderService;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.knowledgeIndexJobService = knowledgeIndexJobService;
        this.knowledgeIndexWorker = knowledgeIndexWorker;
        this.knowledgeDocumentVersionService = knowledgeDocumentVersionService;
        this.chunkSplitter = chunkSplitter;
    }

    @Override
    public String queueReindex(KnowledgeDocument document, KnowledgeDocumentVersion version, String jobType) {
        if (document == null || StringUtils.isBlank(document.getId()) || version == null || StringUtils.isBlank(version.getId())) {
            throw new ServerException(400, "document version is required");
        }
        KnowledgeIndexJob job = new KnowledgeIndexJob();
        job.setKnowledgeBaseId(document.getKnowledgeBaseId()); job.setDocumentId(document.getId());
        job.setDocumentVersionId(version.getId()); job.setJobType(StringUtils.defaultIfBlank(jobType, "reindex"));
        job.setStatus("pending"); job.setRetryCount(0); job.setMaxRetryCount(3);
        knowledgeIndexJobService.save(job);
        dispatchAfterCommit(job.getId());
        return job.getId();
    }

    private void dispatchAfterCommit(String jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            knowledgeIndexWorker.run(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                knowledgeIndexWorker.run(jobId);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(KnowledgeDocument document) {
        reindex(document, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(KnowledgeDocument document, KnowledgeDocumentVersion version) {
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
        String documentVersionId = version == null ? null : version.getId();
        if (StringUtils.isNotBlank(documentVersionId)) {
            // 重试同一版本时仅替换该版本的分块，已发布版本仍可被检索和查看。
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentVersionId, documentVersionId));
        } else {
            // 仅为兼容旧调用保留；异步索引任务必须传入明确的版本。
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentId, document.getId()));
        }

        String indexContent = version == null ? document.getContent()
                : StringUtils.defaultIfBlank(version.getStructuredContent(), version.getContent());
        List<KnowledgeChunkSplitter.Segment> chunks = chunkSplitter.split(indexContent);
        if (chunks.isEmpty()) {
            throw new ServerException(422, "knowledge document content is empty");
        }
        List<String> chunkTexts = new ArrayList<>(chunks.size());
        for (KnowledgeChunkSplitter.Segment segment : chunks) {
            chunkTexts.add(segment.getContent());
        }
        List<List<Double>> embeddings = new ArrayList<>(chunkTexts.size());
        final int embeddingBatchSize = 32;
        for (int batchStart = 0; batchStart < chunkTexts.size(); batchStart += embeddingBatchSize) {
            int batchEnd = Math.min(chunkTexts.size(), batchStart + embeddingBatchSize);
            embeddings.addAll(knowledgeEmbeddingService.embedAll(provider, chunkTexts.subList(batchStart, batchEnd)));
        }
        int index = 0;
        for (KnowledgeChunkSplitter.Segment segment : chunks) {
            String chunkText = segment.getContent();
            String vector = knowledgeEmbeddingService.toVectorLiteral(embeddings.get(index));
            KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
            chunk.setKnowledgeBaseId(knowledgeBase.getId());
            chunk.setDocumentId(document.getId());
            chunk.setDocumentVersionId(documentVersionId);
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkText);
            chunk.setTokenCount(chunkSplitter.estimateTokens(chunkText));
            // 当前分块器尚未提供精确页码；0 表示内容没有可追溯的原始页码。
            chunk.setPageNo(0);
            chunk.setSectionPath(segment.getSectionPath());
            chunk.setContentHash(sha256(chunkText));
            chunk.setMetadata(buildChunkMetadata(document, chunk));
            // 分块首次入库尚未被回答引用，引用次数必须显式写为 0。
            chunk.setReferenceCount(0L);
            chunk.setEmbedding(vector);
            knowledgeDocumentChunkService.saveVectorChunk(chunk);
        }
        if (version != null) {
            KnowledgeDocumentVersion versionUpdate = new KnowledgeDocumentVersion();
            versionUpdate.setId(version.getId());
            versionUpdate.setChunkCount(chunks.size());
            knowledgeDocumentVersionService.updateById(versionUpdate);
        }
        updateDocumentStatus(document.getId(), DOCUMENT_STATUS_DONE, chunks.size());
        updateKnowledgeBaseIndexStatus(knowledgeBase.getId(), KB_INDEX_STATUS_DONE);
    }

    /**
     * 保存最小可用的分块来源元数据；后续页级解析器可在此 JSON 中补充精确页码和章节位置。
     */
    private String buildChunkMetadata(KnowledgeDocument document, KnowledgeDocumentChunk chunk) {
        String sourceType = StringUtils.defaultIfBlank(document.getSourceType(), "text");
        String parserType = StringUtils.defaultIfBlank(document.getParserType(), sourceType);
        return "{\"sourceType\":\"" + jsonEscape(sourceType) + "\",\"parserType\":\""
                + jsonEscape(parserType) + "\",\"pageNo\":" + chunk.getPageNo()
                + ",\"sectionPath\":\"" + jsonEscape(chunk.getSectionPath()) + "\"}";
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new ServerException(500, "failed to calculate chunk content hash");
        }
    }

    private String jsonEscape(String value) {
        return StringUtils.defaultString(value).replace("\\", "\\\\").replace("\"", "\\\"");
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
