package com.aether.knowledge.controller;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.model.KnowledgeDocumentSourceType;
import com.aether.knowledge.model.KnowledgeJobType;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.storage.service.ObjectStorageService;
import com.aether.knowledge.service.impl.KnowledgeDocumentParseWorker;
import com.aether.knowledge.workflow.TransactionAfterCommitExecutor;
import com.aether.knowledge.vo.KnowledgeDocumentVo;
import com.aether.knowledge.vo.KnowledgeDocumentChunkVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.security.MessageDigest;

import com.alibaba.fastjson2.JSONObject;

/**
 * 提供知识库文档相关的 REST 接口。
 */
@Api(tags = "Agent知识库文档 API")
@Validated
@RestController
@Permission(path = "/knowledge/document")
@RequestMapping("/api/knowledge/document")
public class KnowledgeDocumentController {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentController.class);

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeDocumentIndexService knowledgeDocumentIndexService;
    private final KnowledgeDocumentVersionService knowledgeDocumentVersionService;
    private final ObjectStorageService objectStorageService;
    private final KnowledgeDocumentParseWorker documentParseWorker;
    private final TransactionAfterCommitExecutor afterCommitExecutor;
    private final String knowledgeBucket;
    private final KnowledgeAccessService knowledgeAccessService;
    private final KnowledgeDocumentWorkflowService workflowService;

    /**
     * 创建 {@code KnowledgeDocumentController} 实例。
     */
    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService,
                                       KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                       KnowledgeDocumentIndexService knowledgeDocumentIndexService,
                                       KnowledgeDocumentVersionService knowledgeDocumentVersionService,
                                       ObjectStorageService objectStorageService,
                                       KnowledgeDocumentParseWorker documentParseWorker,
                                       TransactionAfterCommitExecutor afterCommitExecutor,
                                       KnowledgeAccessService knowledgeAccessService,
                                       KnowledgeDocumentWorkflowService workflowService,
                                       @Value("${knowledge.storage.bucket:${MINIO_KNOWLEDGE_BUCKET:aether-knowledge}}") String knowledgeBucket) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.knowledgeDocumentIndexService = knowledgeDocumentIndexService;
        this.knowledgeDocumentVersionService = knowledgeDocumentVersionService;
        this.objectStorageService = objectStorageService;
        this.documentParseWorker = documentParseWorker;
        this.afterCommitExecutor = afterCommitExecutor;
        this.knowledgeAccessService = knowledgeAccessService;
        this.workflowService = workflowService;
        this.knowledgeBucket = knowledgeBucket;
    }

    /**
     * 按当前用户可见知识库分页查询文档。
     */
    @ApiOperation("文档列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeDocumentVo>> list(@RequestBody KnowledgeDocumentVo vo) {
        List<String> readableIds = knowledgeAccessService.readableKnowledgeBaseIds();
        Page<KnowledgeDocument> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        if (readableIds.isEmpty()) {
            return WebResponse.Page(Collections.emptyList(), 0L);
        }
        Wrapper<KnowledgeDocument> wrapper = Wrappers.lambdaQuery(KnowledgeDocument.class)
                .in(KnowledgeDocument::getKnowledgeBaseId, readableIds)
                .eq(StringUtils.isNotBlank(vo.getKnowledgeBaseId()), KnowledgeDocument::getKnowledgeBaseId, vo.getKnowledgeBaseId())
                .like(StringUtils.isNotBlank(vo.getTitle()), KnowledgeDocument::getTitle, vo.getTitle())
                .eq(vo.getStatus() != null, KnowledgeDocument::getStatus, vo.getStatus())
                .eq(StringUtils.isNotBlank(vo.getReviewStatus()), KnowledgeDocument::getReviewStatus, vo.getReviewStatus())
                .eq(KnowledgeDocument::getDeleted, false)
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        Page<KnowledgeDocument> result = knowledgeDocumentService.page(page, wrapper);
        List<KnowledgeDocumentVo> list = result.getRecords().stream().map(item -> {
            KnowledgeDocumentVo itemVo = new KnowledgeDocumentVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 查询文档基础信息，并执行知识库可读性校验。
     */
    @ApiOperation("文档详情")
    @GetMapping("/{id}")
    public WebResponse<KnowledgeDocumentVo> detail(@PathVariable @NotBlank String id) {
        KnowledgeDocument document = getExisting(id);
        KnowledgeDocumentVo vo = new KnowledgeDocumentVo();
        BeanUtils.copyProperties(document, vo);
        return WebResponse.OK(vo);
    }

    /**
     * 创建文本/Markdown 文档草稿，并按知识库配置触发 AI 审核。
     */
    @ApiOperation("新增文档并同步索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<String> save(@RequestBody KnowledgeDocumentVo vo) {
        KnowledgeBase base = knowledgeAccessService.requireWritable(vo.getKnowledgeBaseId());
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(vo.getKnowledgeBaseId());
        document.setTitle(vo.getTitle());
        document.setContent(null);
        document.setSourceUrl(vo.getSourceUrl());
        document.setSourceType(StringUtils.defaultIfBlank(vo.getSourceType(), KnowledgeDocumentSourceType.TEXT));
        document.setParserType(vo.getParserType());
        if (document.getStatus() == null) {
            document.setStatus(0);
        }
        boolean saved = knowledgeDocumentService.save(document);
        if (saved) {
            KnowledgeDocument snapshot = knowledgeDocumentService.getById(document.getId());
            snapshot.setContent(vo.getContent());
            KnowledgeDocumentVersion draft = workflowService.createDraft(snapshot, null);
            startAiReviewIfConfigured(base, draft);
        }
        return WebResponse.OK(saved ? I18nUtils.getMessage("knowledge.document.create.success") : I18nUtils.getMessage("knowledge.document.create.fail"), document.getId());
    }

    /**
     * 上传单个知识文档。原文件持久化成功后，后台使用 AnyDoc 转换为
     * Markdown；AnyDoc 不可用或不支持时由本地解析器处理已支持的格式。
     */
    @ApiOperation("Upload knowledge document")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/upload")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<String> upload(@RequestParam("knowledgeBaseId") String knowledgeBaseId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "title", required = false) String title) throws Exception {
        knowledgeAccessService.requireWritable(knowledgeBaseId);
        String operatorId = knowledgeAccessService.currentAdminId();
        if (file == null || file.isEmpty() || file.getSize() > 50L * 1024 * 1024)
            throw new ServerException(422, I18nUtils.getMessage("knowledge.document.file.invalid"));
        String name = StringUtils.defaultIfBlank(file.getOriginalFilename(), "document.txt");
        byte[] bytes = file.getBytes();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setTitle(StringUtils.defaultIfBlank(title, name));
        document.setSourceType(KnowledgeDocumentSourceType.FILE);
        String contentType = storageContentType(name, file.getContentType());
        document.setOriginalFileName(name);
        document.setFileExtension(name.substring(name.lastIndexOf('.') + 1));
        document.setMimeType(contentType);
        document.setFileSize(file.getSize());
        document.setFileChecksum(sha256(bytes));
        document.setContent(null);
        document.setStatus(1);
        document.setIndexStatus(0);
        document.setCurrentVersionNo(0);
        if (!knowledgeDocumentService.save(document))
            throw new ServerException(500, I18nUtils.getMessage("knowledge.document.create.fail"));
        String key = "knowledge/" + knowledgeBaseId + "/" + document.getId() + "/1/" + name.replaceAll("[^a-zA-Z0-9._-]", "_");
        boolean uploaded = false;
        try {
            objectStorageService.upload(knowledgeBucket, key, bytes, contentType);
            uploaded = true;
            KnowledgeDocument storage = new KnowledgeDocument();
            storage.setId(document.getId());
            storage.setStorageBucket(knowledgeBucket);
            storage.setStorageObjectKey(key);
            knowledgeDocumentService.updateById(storage);
            afterCommitExecutor.execute(() -> documentParseWorker.submit(document.getId(), operatorId));
            return WebResponse.OK(I18nUtils.getMessage("knowledge.document.upload.accepted-for-review"), document.getId());
        } catch (Exception e) {
            knowledgeDocumentService.removeById(document.getId());
            if (uploaded) {
                try {
                    objectStorageService.removeObject(knowledgeBucket, key);
                } catch (Exception cleanupError) {
                    log.warn("Failed to clean up abandoned knowledge object: {}", key, cleanupError);
                }
            }
            throw e;
        }
    }

    /**
     * 批量上传文档；单个文件失败不会影响其它文件继续处理。
     */
    @ApiOperation("Batch upload knowledge documents")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/upload/batch")
    public WebResponse<List<UploadResult>> uploadBatch(@RequestParam("knowledgeBaseId") String knowledgeBaseId,
                                                       @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0)
            throw new ServerException(422, I18nUtils.getMessage("knowledge.document.file.invalid"));
        List<UploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            UploadResult result = new UploadResult();
            result.setFileName(file == null ? null : file.getOriginalFilename());
            try {
                WebResponse<String> response = upload(knowledgeBaseId, file, null);
                result.setSuccess(true);
                result.setVersionId(response.getData());
            } catch (Exception e) {
                log.warn("知识文档批量上传失败: file={}", result.getFileName(), e);
                result.setSuccess(false);
                result.setMessage(e.getMessage());
            }
            results.add(result);
        }
        return WebResponse.OK(results);
    }

    /**
     * 批量上传中单个文件的处理结果。
     */
    public static class UploadResult {
        private String fileName;
        private boolean success;
        private String versionId;
        private String message;

        /**
         * 获取文件Name。
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * 处理set文件Name。
         */
        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        /**
         * 判断是否为Success。
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 处理setSuccess。
         */
        public void setSuccess(boolean success) {
            this.success = success;
        }

        /**
         * 获取VersionId。
         */
        public String getVersionId() {
            return versionId;
        }

        /**
         * 处理setVersionId。
         */
        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }

        /**
         * 获取消息。
         */
        public String getMessage() {
            return message;
        }

        /**
         * 处理set消息。
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * Ensures UTF-8 response metadata for text formats, including old stored objects on preview.
     */
    private String storageContentType(String fileName, String fallback) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(fileName));
        if (lower.endsWith(".md")) return "text/markdown; charset=UTF-8";
        if (lower.endsWith(".txt")) return "text/plain; charset=UTF-8";
        return fallback;
    }

    /**
     * 预览Url。
     */
    @ApiOperation("Preview knowledge document")
    @GetMapping("/{id}/preview-url")
    public WebResponse<String> previewUrl(@PathVariable @NotBlank String id) {
        KnowledgeDocument document = getExisting(id);
        if (StringUtils.isBlank(document.getStorageObjectKey()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.source-file.not-found"));
        return WebResponse.OK(I18nUtils.getMessage("knowledge.document.preview-url.ready"), objectStorageService.presignedGetUrl(
                document.getStorageBucket(), document.getStorageObjectKey(), 600,
                storageContentType(document.getOriginalFileName(), document.getMimeType())));
    }

    /**
     * 文档版本列表。
     */
    @ApiOperation("文档版本列表")
    @GetMapping("/{id}/versions")
    public WebResponse<List<KnowledgeDocumentVersion>> versions(@PathVariable @NotBlank String id) {
        getExisting(id);
        return WebResponse.OK(knowledgeDocumentVersionService.list(Wrappers.lambdaQuery(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, id)
                .eq(KnowledgeDocumentVersion::getDeleted, false)
                .orderByDesc(KnowledgeDocumentVersion::getVersionNo)));
    }

    /**
     * 处理version详情。
     */
    @GetMapping("/version/{versionId}")
    public WebResponse<KnowledgeDocumentVersion> versionDetail(@PathVariable @NotBlank String versionId) {
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        KnowledgeDocument document = getExisting(version.getKnowledgeDocumentId());
        knowledgeAccessService.requireReadable(document.getKnowledgeBaseId());
        return WebResponse.OK(version);
    }

    /**
     * Preview original document version file。
     */
    @ApiOperation("Preview original document version file")
    @GetMapping("/version/{versionId}/preview-url")
    public WebResponse<String> versionPreviewUrl(@PathVariable @NotBlank String versionId) {
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        KnowledgeDocument document = getExisting(version.getKnowledgeDocumentId());
        knowledgeAccessService.requireReadable(document.getKnowledgeBaseId());
        String objectKey = StringUtils.defaultIfBlank(version.getStorageObjectKey(), document.getStorageObjectKey());
        if (StringUtils.isBlank(objectKey)) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.source-file.not-found"));
        }
        return WebResponse.OK(I18nUtils.getMessage("knowledge.document.preview-url.ready"), objectStorageService.presignedGetUrl(
                StringUtils.defaultIfBlank(version.getStorageBucket(), document.getStorageBucket()), objectKey, 600,
                storageContentType(document.getOriginalFileName(), document.getMimeType())));
    }



    /**
     * 文档版本分块列表。
     */
    @ApiOperation("文档版本分块列表")
    @GetMapping("/version/{versionId}/chunk/list")
    public WebResponse<List<KnowledgeDocumentChunkVo>> versionChunks(@PathVariable @NotBlank String versionId) {
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        KnowledgeDocument versionDocument = getExisting(version.getKnowledgeDocumentId());
        knowledgeAccessService.requireReadable(versionDocument.getKnowledgeBaseId());
        List<KnowledgeDocumentChunkVo> chunks = knowledgeDocumentChunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class)
                        .eq(KnowledgeDocumentChunk::getDocumentVersionId, versionId)
                        .eq(KnowledgeDocumentChunk::getDeleted, false)
                        .orderByAsc(KnowledgeDocumentChunk::getChunkIndex))
                .stream().map(chunk -> {
                    KnowledgeDocumentChunkVo vo = new KnowledgeDocumentChunkVo();
                    vo.setId(chunk.getId());
                    vo.setChunkNo(chunk.getChunkIndex());
                    vo.setContent(chunk.getContent());
                    vo.setTokenCount(chunk.getTokenCount());
                    vo.setCreatedAt(chunk.getCreatedAt());
                    return vo;
                }).collect(Collectors.toList());
        return WebResponse.OK(chunks);
    }

    /**
     * 回滚到指定文档版本并异步索引。
     */
    @ApiOperation("回滚到指定文档版本并异步索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping({"/version/{versionId}/rollback", "/version/{versionId}/revise"})
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<String> rollback(@PathVariable @NotBlank String versionId) {
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        KnowledgeDocument document = getExisting(version.getKnowledgeDocumentId());
        knowledgeAccessService.requireWritable(document.getKnowledgeBaseId());
        KnowledgeDocument snapshot = knowledgeDocumentService.getById(document.getId());
        snapshot.setContent(version.getContent());
        snapshot.setStorageBucket(version.getStorageBucket());
        snapshot.setStorageObjectKey(version.getStorageObjectKey());
        snapshot.setFileChecksum(version.getFileChecksum());
        KnowledgeDocumentVersion draft = workflowService.createDraft(snapshot, version.getId());
        startAiReviewIfConfigured(knowledgeAccessService.requireWritable(document.getKnowledgeBaseId()), draft);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.document.version.rollback.success"), draft.getId());
    }

    /**
     * 更新当前请求。
     */
    @ApiOperation("Update knowledge document")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody KnowledgeDocumentVo vo) {
        KnowledgeDocument existing = getExisting(id);
        knowledgeAccessService.requireWritable(existing.getKnowledgeBaseId());
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(vo.getTitle());
        document.setSourceUrl(vo.getSourceUrl());
        document.setParserType(vo.getParserType());
        document.setId(id);
        // A generic document update must not move data into another knowledge base.
        document.setKnowledgeBaseId(existing.getKnowledgeBaseId());
        boolean updated = knowledgeDocumentService.updateById(document);
        if (updated) {
            KnowledgeDocumentVersion draft;
            if (StringUtils.isNotBlank(existing.getDraftVersionId())) {
                draft = workflowService.updateDraft(existing.getDraftVersionId(), vo.getContent(), vo.getExpectedChecksum());
            } else {
                KnowledgeDocument snapshot = knowledgeDocumentService.getById(id);
                snapshot.setContent(vo.getContent());
                draft = workflowService.createDraft(snapshot, resolveCurrentVersionId(existing));
            }
        }
        return WebResponse.OK(updated ? I18nUtils.getMessage("knowledge.document.update.success") : I18nUtils.getMessage("knowledge.document.update.fail"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除文档")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        KnowledgeDocument existing = getExisting(id);
        knowledgeAccessService.requireWritable(existing.getKnowledgeBaseId());
        if (StringUtils.isNotBlank(existing.getSubmittedVersionId())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.delete.active-workflow"));
        }
        boolean removed = knowledgeDocumentService.removeById(id);
        if (removed) {
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentId, id));
            knowledgeDocumentVersionService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                    .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, id));
        }
        if (removed && StringUtils.isNotBlank(existing.getStorageBucket()) && StringUtils.isNotBlank(existing.getStorageObjectKey())) {
            try {
                objectStorageService.removeObject(existing.getStorageBucket(), existing.getStorageObjectKey());
            } catch (Exception e) {
                log.warn("Failed to remove knowledge source object: {}", existing.getStorageObjectKey(), e);
            }
        }
        return WebResponse.OK(removed ? I18nUtils.getMessage("knowledge.document.delete.success") : I18nUtils.getMessage("knowledge.document.delete.fail"));
    }

    /**
     * 重建文档索引。
     */
    @ApiOperation("重建文档索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/{id}/reindex")
    public WebResponse<Void> reindex(@PathVariable @NotBlank String id) {
        KnowledgeDocument document = getExisting(id);
        knowledgeAccessService.requireWritable(document.getKnowledgeBaseId());
        if (document.getCurrentVersionNo() == null || document.getCurrentVersionNo() <= 0) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.published-version.required"));
        }
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getOne(
                Wrappers.lambdaQuery(KnowledgeDocumentVersion.class)
                        .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, id)
                        .eq(KnowledgeDocumentVersion::getVersionNo, document.getCurrentVersionNo())
                        .eq(KnowledgeDocumentVersion::getDeleted, false), false);
        if (version == null)
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.published-version.not-found"));
        String jobId = knowledgeDocumentIndexService.queueReindex(document, version, KnowledgeJobType.REINDEX);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.document.reindex.queued", new Object[]{jobId}));
    }

    /**
     * 更新Draft。
     */
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PutMapping("/version/{versionId}/draft")
    public WebResponse<KnowledgeDocumentVersion> updateDraft(@PathVariable @NotBlank String versionId,
                                                             @RequestBody com.aether.knowledge.vo.KnowledgeDraftUpdateVo vo) {
        return WebResponse.OK(I18nUtils.getMessage("knowledge.document.draft.save.success"),
                workflowService.updateDraft(versionId, vo.getContent(), vo.getExpectedChecksum()));
    }

    /**
     * 处理startAi审核。
     */
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/version/{versionId}/ai-review")
    public WebResponse<String> startAiReview(@PathVariable @NotBlank String versionId) {
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.start.success"), workflowService.startAiReview(versionId));
    }

    /**
     * 提交当前请求。
     */
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/version/{versionId}/submit")
    public WebResponse<String> submit(@PathVariable @NotBlank String versionId,
                                      @RequestBody(required = false) com.aether.knowledge.vo.KnowledgeReviewDecisionVo vo) {
        KnowledgeReviewTask task = workflowService.submit(versionId, vo == null ? null : vo.getComment());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.document.submit.success"), task.getId());
    }

    /**
     * 获取Existing。
     */
    private KnowledgeDocument getExisting(String id) {
        KnowledgeDocument document = knowledgeDocumentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        }
        knowledgeAccessService.requireReadable(document.getKnowledgeBaseId());
        return document;
    }

    /**
     * 解析当前VersionId。
     */
    private String resolveCurrentVersionId(KnowledgeDocument document) {
        if (document.getCurrentVersionNo() == null || document.getCurrentVersionNo() <= 0) return null;
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getOne(
                Wrappers.lambdaQuery(KnowledgeDocumentVersion.class)
                        .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, document.getId())
                        .eq(KnowledgeDocumentVersion::getVersionNo, document.getCurrentVersionNo())
                        .eq(KnowledgeDocumentVersion::getDeleted, false), false);
        return version == null ? null : version.getId();
    }

    /**
     * 处理startAi审核IfConfigured。
     */
    private void startAiReviewIfConfigured(KnowledgeBase base, KnowledgeDocumentVersion draft) {
        boolean autoStart = true;
        if (StringUtils.isNotBlank(base.getReviewConfig())) {
            try {
                Boolean configured = JSONObject.parseObject(base.getReviewConfig()).getBoolean("autoAiReview");
                if (configured != null) autoStart = configured;
            } catch (Exception e) {
                log.warn("Invalid knowledge review config: knowledgeBaseId={}", base.getId(), e);
            }
        }
        if (autoStart) workflowService.startAiReview(draft.getId());
    }

    /**
     * 处理sha256。
     */
    private String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : hash) out.append(String.format("%02x", b));
        return out.toString();
    }
}
