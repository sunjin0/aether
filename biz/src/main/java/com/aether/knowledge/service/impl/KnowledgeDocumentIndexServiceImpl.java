package com.aether.knowledge.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.model.KnowledgeIndexJobStatus;
import com.aether.knowledge.model.KnowledgeDocumentSourceType;
import com.aether.knowledge.model.KnowledgeJobType;
import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.workflow.TransactionAfterCommitExecutor;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 实现知识库文档索引业务服务。
 */
@Service
public class KnowledgeDocumentIndexServiceImpl implements KnowledgeDocumentIndexService {

    private static final int DOCUMENT_STATUS_INDEXING = 1;
    private static final int DOCUMENT_STATUS_DONE = 2;
    private static final int KB_INDEX_STATUS_DONE = 2;
    private static final int EMBEDDING_BATCH_SIZE = 10;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ModelProviderService modelProviderService;
    private final ModelCatalogService modelCatalogService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeIndexJobService knowledgeIndexJobService;
    private final KnowledgeIndexWorker knowledgeIndexWorker;
    private final KnowledgeDocumentVersionService knowledgeDocumentVersionService;
    private final KnowledgeChunkSplitter chunkSplitter;
    private final TransactionAfterCommitExecutor afterCommitExecutor;

    /**
     * 创建 {@code KnowledgeDocumentIndexServiceImpl} 实例。
     */
    public KnowledgeDocumentIndexServiceImpl(KnowledgeDocumentService knowledgeDocumentService,
                                             KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                             KnowledgeBaseService knowledgeBaseService,
                                             ModelProviderService modelProviderService,
                                             ModelCatalogService modelCatalogService,
                                             KnowledgeEmbeddingService knowledgeEmbeddingService,
                                             KnowledgeIndexJobService knowledgeIndexJobService,
                                             KnowledgeIndexWorker knowledgeIndexWorker,
                                             KnowledgeDocumentVersionService knowledgeDocumentVersionService,
                                             KnowledgeChunkSplitter chunkSplitter,
                                             TransactionAfterCommitExecutor afterCommitExecutor) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.knowledgeIndexJobService = knowledgeIndexJobService;
        this.knowledgeIndexWorker = knowledgeIndexWorker;
        this.knowledgeDocumentVersionService = knowledgeDocumentVersionService;
        this.chunkSplitter = chunkSplitter;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    /**
     * 处理queueReindex。
     */
    @Override
    public String queueReindex(KnowledgeDocument document, KnowledgeDocumentVersion version, String jobType) {
        if (document == null || StringUtils.isBlank(document.getId()) || version == null || StringUtils.isBlank(version.getId())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.document-version.required"));
        }
        KnowledgeIndexJob job = new KnowledgeIndexJob();
        job.setKnowledgeBaseId(document.getKnowledgeBaseId());
        job.setDocumentId(document.getId());
        job.setDocumentVersionId(version.getId());
        job.setJobType(StringUtils.defaultIfBlank(jobType, KnowledgeJobType.REINDEX));
        job.setStatus(KnowledgeIndexJobStatus.PENDING);
        job.setRetryCount(0);
        job.setMaxRetryCount(3);
        if (!knowledgeIndexJobService.save(job) || StringUtils.isBlank(job.getId())) {
            throw new ServerException(500,
                    I18nUtils.getMessage("knowledge.index-job.create.failed"));
        }
        afterCommitExecutor.execute(() -> knowledgeIndexWorker.run(job.getId()));
        return job.getId();
    }

    /**
     * 处理reindex。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(KnowledgeDocument document) {
        reindex(document, null);
    }

    /**
     * 处理reindex。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(KnowledgeDocument document, KnowledgeDocumentVersion version) {
        if (document == null || StringUtils.isBlank(document.getId())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.document.required"));
        }
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(document.getKnowledgeBaseId());
        if (knowledgeBase == null || Boolean.TRUE.equals(knowledgeBase.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.base.not-found"));
        }
        ModelProvider provider = getEmbeddingProvider(knowledgeBase);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.model-provider.not-found"));
        }

        String documentVersionId = version == null ? null : version.getId();
        String indexContent = version == null ? document.getContent()
                : StringUtils.defaultIfBlank(version.getStructuredContent(), version.getContent());
        List<KnowledgeChunkSplitter.Segment> chunks = chunkSplitter.split(indexContent);
        if (chunks.isEmpty()) {
            throw new ServerException(422, I18nUtils.getMessage("knowledge.document.content.empty"));
        }
        Map<String, String> existingEmbeddings = loadExistingEmbeddings(document.getId(), documentVersionId, provider);
        Map<String, String> chunkTextByHash = new LinkedHashMap<>();
        List<String> chunkHashes = new ArrayList<>(chunks.size());
        for (KnowledgeChunkSplitter.Segment segment : chunks) {
            String embeddingInput = buildEmbeddingInput(segment);
            String contentHash = sha256(embeddingInput);
            chunkHashes.add(contentHash);
            if (!existingEmbeddings.containsKey(contentHash)) {
                chunkTextByHash.putIfAbsent(contentHash, embeddingInput);
            }
        }
        List<String> uncachedTexts = new ArrayList<>(chunkTextByHash.values());
        List<List<Double>> embeddings = new ArrayList<>(uncachedTexts.size());
        for (int batchStart = 0; batchStart < uncachedTexts.size(); batchStart += EMBEDDING_BATCH_SIZE) {
            int batchEnd = Math.min(uncachedTexts.size(), batchStart + EMBEDDING_BATCH_SIZE);
            embeddings.addAll(knowledgeEmbeddingService.embedAll(provider, uncachedTexts.subList(batchStart, batchEnd)));
        }
        int embeddingIndex = 0;
        for (String contentHash : chunkTextByHash.keySet()) {
            existingEmbeddings.put(contentHash, knowledgeEmbeddingService.toVectorLiteral(embeddings.get(embeddingIndex++)));
        }

        updateDocumentStatus(document.getId(), DOCUMENT_STATUS_INDEXING, null);
        if (StringUtils.isNotBlank(documentVersionId)) {
            // Replace only after all remote embedding requests have completed successfully.
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentVersionId, documentVersionId));
        } else {
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentId, document.getId()));
        }
        int index = 0;
        for (KnowledgeChunkSplitter.Segment segment : chunks) {
            String chunkText = segment.getContent();
            String contentHash = chunkHashes.get(index);
            String vector = existingEmbeddings.get(contentHash);
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
            chunk.setContentHash(contentHash);
            chunk.setMetadata(buildChunkMetadata(document, chunk, provider));
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
     * 加载ExistingEmbeddings。
     */
    private Map<String, String> loadExistingEmbeddings(String documentId, String documentVersionId, ModelProvider provider) {
        List<KnowledgeDocumentChunk> existing = knowledgeDocumentChunkService.list(
                Wrappers.lambdaQuery(KnowledgeDocumentChunk.class)
                        .eq(StringUtils.isNotBlank(documentVersionId), KnowledgeDocumentChunk::getDocumentVersionId, documentVersionId)
                        .eq(StringUtils.isBlank(documentVersionId), KnowledgeDocumentChunk::getDocumentId, documentId)
                        .eq(KnowledgeDocumentChunk::getDeleted, false));
        Map<String, String> embeddings = new HashMap<>();
        if (existing != null) {
            for (KnowledgeDocumentChunk chunk : existing) {
                if (StringUtils.isNotBlank(chunk.getContentHash()) && StringUtils.isNotBlank(chunk.getEmbedding())
                        && matchesEmbeddingModel(chunk.getMetadata(), provider)) {
                    embeddings.putIfAbsent(chunk.getContentHash(), chunk.getEmbedding());
                }
            }
        }
        return embeddings;
    }

    /**
     * 保存最小可用的分块来源元数据；后续页级解析器可在此 JSON 中补充精确页码和章节位置。
     */
    private String buildChunkMetadata(KnowledgeDocument document, KnowledgeDocumentChunk chunk, ModelProvider provider) {
        String sourceType = StringUtils.defaultIfBlank(document.getSourceType(), KnowledgeDocumentSourceType.TEXT);
        String parserType = StringUtils.defaultIfBlank(document.getParserType(), sourceType);
        return "{\"sourceType\":\"" + jsonEscape(sourceType) + "\",\"parserType\":\""
                + jsonEscape(parserType) + "\",\"pageNo\":" + chunk.getPageNo()
                + ",\"sectionPath\":\"" + jsonEscape(chunk.getSectionPath())
                + "\",\"embeddingProviderId\":\"" + jsonEscape(provider.getId())
                + "\",\"embeddingModel\":\"" + jsonEscape(provider.getDefaultModel()) + "\"}";
    }

    /**
     * 构建EmbeddingInput。
     */
    private String buildEmbeddingInput(KnowledgeChunkSplitter.Segment segment) {
        String sectionPath = StringUtils.defaultIfBlank(segment.getSectionPath(), "ROOT");
        return "章节：" + sectionPath + "\n内容：\n" + segment.getContent();
    }

    /**
     * 处理matchesEmbedding模型。
     */
    private boolean matchesEmbeddingModel(String metadata, ModelProvider provider) {
        if (StringUtils.isBlank(metadata)) {
            return false;
        }
        try {
            JSONObject json = JSONObject.parseObject(metadata);
            return StringUtils.equals(provider.getId(), json.getString("embeddingProviderId"))
                    && StringUtils.equals(provider.getDefaultModel(), json.getString("embeddingModel"));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 处理sha256。
     */
    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new ServerException(500, I18nUtils.getMessage("knowledge.chunk.content-hash.failed"));
        }
    }

    /**
     * 处理jsonEscape。
     */
    private String jsonEscape(String value) {
        return StringUtils.defaultString(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 更新文档状态。
     */
    private void updateDocumentStatus(String documentId, Integer status, Integer chunkCount) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(status);
        if (chunkCount != null) {
            update.setChunkCount(chunkCount);
        }
        knowledgeDocumentService.updateById(update);
    }

    /**
     * 更新知识库Base索引状态。
     */
    private void updateKnowledgeBaseIndexStatus(String knowledgeBaseId, Integer indexStatus) {
        KnowledgeBase update = new KnowledgeBase();
        update.setId(knowledgeBaseId);
        update.setIndexStatus(indexStatus);
        knowledgeBaseService.updateById(update);
    }

    /**
     * 获取EmbeddingProvider。
     */
    private ModelProvider getEmbeddingProvider(KnowledgeBase knowledgeBase) {
        if (StringUtils.isBlank(knowledgeBase.getEmbeddingModelId())) return null;
        try {
            return modelCatalogService.resolveProvider(knowledgeBase.getEmbeddingModelId(), "EMBEDDING");
        } catch (Exception e) {
            return null;
        }
    }
}
