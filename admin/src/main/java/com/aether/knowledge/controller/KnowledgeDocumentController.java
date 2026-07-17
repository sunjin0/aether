package com.aether.knowledge.controller;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.storage.service.ObjectStorageService;
import com.aether.knowledge.service.impl.KnowledgeDocumentContentExtractor;
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

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;
import java.security.MessageDigest;

@Api(tags = "Agent知识库文档 API")
@Validated
@RestController
@Permission(path = "/knowledge/document")
@RequestMapping("/api/knowledge/document")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeDocumentIndexService knowledgeDocumentIndexService;
    private final KnowledgeDocumentVersionService knowledgeDocumentVersionService;
    private final ObjectStorageService objectStorageService;
    private final KnowledgeDocumentContentExtractor contentExtractor;
    private final String knowledgeBucket;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService,
                                   KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                   KnowledgeDocumentIndexService knowledgeDocumentIndexService,
                                   KnowledgeDocumentVersionService knowledgeDocumentVersionService,
                                   ObjectStorageService objectStorageService,
                                   KnowledgeDocumentContentExtractor contentExtractor,
                                   @Value("${knowledge.storage.bucket:${MINIO_KNOWLEDGE_BUCKET:aether-knowledge}}") String knowledgeBucket) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.knowledgeDocumentIndexService = knowledgeDocumentIndexService;
        this.knowledgeDocumentVersionService = knowledgeDocumentVersionService;
        this.objectStorageService = objectStorageService;
        this.contentExtractor = contentExtractor;
        this.knowledgeBucket = knowledgeBucket;
    }

    @ApiOperation("文档列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeDocumentVo>> list(@RequestBody KnowledgeDocumentVo vo) {
        Page<KnowledgeDocument> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<KnowledgeDocument> wrapper = Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(StringUtils.isNotBlank(vo.getKnowledgeBaseId()), KnowledgeDocument::getKnowledgeBaseId, vo.getKnowledgeBaseId())
                .like(StringUtils.isNotBlank(vo.getTitle()), KnowledgeDocument::getTitle, vo.getTitle())
                .eq(vo.getStatus() != null, KnowledgeDocument::getStatus, vo.getStatus())
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

    @ApiOperation("文档详情")
    @GetMapping("/{id}")
    public WebResponse<KnowledgeDocumentVo> detail(@PathVariable @NotBlank String id) {
        KnowledgeDocument document = getExisting(id);
        KnowledgeDocumentVo vo = new KnowledgeDocumentVo();
        BeanUtils.copyProperties(document, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增文档并同步索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody KnowledgeDocumentVo vo) {
        KnowledgeDocument document = new KnowledgeDocument();
        BeanUtils.copyProperties(vo, document);
        if (document.getStatus() == null) {
            document.setStatus(0);
        }
        boolean saved = knowledgeDocumentService.save(document);
        if (saved) {
            queueVersion(document, "create");
        }
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), document.getId());
    }

    @ApiOperation("Upload knowledge document")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/upload")
    public WebResponse<String> upload(@RequestParam("knowledgeBaseId") String knowledgeBaseId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "title", required = false) String title) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 50L * 1024 * 1024) throw new ServerException(422, "invalid knowledge file");
        String name = StringUtils.defaultIfBlank(file.getOriginalFilename(), "document.txt");
        String lower = name.toLowerCase();
        if (!(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".pdf") || lower.endsWith(".docx"))) throw new ServerException(422, "only txt, md, pdf and docx are supported");
        byte[] bytes = file.getBytes();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId); document.setTitle(StringUtils.defaultIfBlank(title, name)); document.setSourceType("file");
        document.setOriginalFileName(name); document.setFileExtension(name.substring(name.lastIndexOf('.') + 1)); document.setMimeType(file.getContentType());
        document.setFileSize(file.getSize()); document.setFileChecksum(sha256(bytes)); document.setContent(contentExtractor.extract(name, bytes));
        document.setStatus(0); document.setIndexStatus(0); document.setCurrentVersionNo(0); knowledgeDocumentService.save(document);
        String key = "knowledge/" + knowledgeBaseId + "/" + document.getId() + "/1/" + name.replaceAll("[^a-zA-Z0-9._-]", "_");
        objectStorageService.upload(knowledgeBucket, key, file);
        KnowledgeDocument storage = new KnowledgeDocument(); storage.setId(document.getId()); storage.setStorageBucket(knowledgeBucket); storage.setStorageObjectKey(key); knowledgeDocumentService.updateById(storage);
        return WebResponse.OK("upload accepted", queueVersion(knowledgeDocumentService.getById(document.getId()), "upload"));
    }

    @ApiOperation("Preview knowledge document")
    @GetMapping("/{id}/preview-url")
    public WebResponse<String> previewUrl(@PathVariable @NotBlank String id) {
        KnowledgeDocument document = getExisting(id);
        if (StringUtils.isBlank(document.getStorageObjectKey())) throw new ServerException(404, "source file not found");
        return WebResponse.OK("",objectStorageService.presignedGetUrl(document.getStorageBucket(), document.getStorageObjectKey(), 600));
    }

    @ApiOperation("文档版本列表")
    @GetMapping("/{id}/versions")
    public WebResponse<List<KnowledgeDocumentVersion>> versions(@PathVariable @NotBlank String id) {
        getExisting(id);
        return WebResponse.OK(knowledgeDocumentVersionService.list(Wrappers.lambdaQuery(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, id)
                .eq(KnowledgeDocumentVersion::getDeleted, false)
                .orderByDesc(KnowledgeDocumentVersion::getVersionNo)));
    }

    @ApiOperation("文档版本分块列表")
    @GetMapping("/version/{versionId}/chunk/list")
    public WebResponse<List<KnowledgeDocumentChunkVo>> versionChunks(@PathVariable @NotBlank String versionId) {
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, "document version not found");
        }
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

    @ApiOperation("回滚到指定文档版本并异步索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/version/{versionId}/rollback")
    public WebResponse<String> rollback(@PathVariable @NotBlank String versionId) {
        KnowledgeDocumentVersion version = knowledgeDocumentVersionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) throw new ServerException(404, "document version not found");
        KnowledgeDocument document = getExisting(version.getKnowledgeDocumentId());
        KnowledgeDocument update = new KnowledgeDocument(); update.setId(document.getId()); update.setContent(version.getContent());
        update.setStorageBucket(version.getStorageBucket()); update.setStorageObjectKey(version.getStorageObjectKey()); update.setFileChecksum(version.getFileChecksum());
        knowledgeDocumentService.updateById(update);
        return WebResponse.OK(queueVersion(knowledgeDocumentService.getById(document.getId()), "rollback"));
    }

    @ApiOperation("Update knowledge document")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody KnowledgeDocumentVo vo) {
        getExisting(id);
        KnowledgeDocument document = new KnowledgeDocument();
        BeanUtils.copyProperties(vo, document);
        document.setId(id);
        boolean updated = knowledgeDocumentService.updateById(document);
        if (updated) {
            queueVersion(knowledgeDocumentService.getById(id), "update");
        }
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除文档")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = knowledgeDocumentService.removeById(id);
        if (removed) {
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentId, id));
            knowledgeDocumentVersionService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                    .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, id));
        }
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("重建文档索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/{id}/reindex")
    public WebResponse<Void> reindex(@PathVariable @NotBlank String id) {
        String jobId = queueVersion(getExisting(id), "reindex");
        return WebResponse.OK(I18nUtils.getMessage("update.success") + ": " + jobId);
    }

    private KnowledgeDocument getExisting(String id) {
        KnowledgeDocument document = knowledgeDocumentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        return document;
    }

    private String queueVersion(KnowledgeDocument document, String jobType) {
        // 以已有最大版本号递增，避免多个异步任务尚未发布时产生相同版本号。
        List<KnowledgeDocumentVersion> existingVersions = knowledgeDocumentVersionService.list(
                Wrappers.lambdaQuery(KnowledgeDocumentVersion.class)
                        .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, document.getId())
                        .eq(KnowledgeDocumentVersion::getDeleted, false)
                        .orderByDesc(KnowledgeDocumentVersion::getVersionNo));
        int versionNo = existingVersions.isEmpty() ? 1 : existingVersions.get(0).getVersionNo() + 1;
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setKnowledgeDocumentId(document.getId()); version.setVersionNo(versionNo); version.setContent(document.getContent());
        version.setStorageBucket(document.getStorageBucket()); version.setStorageObjectKey(document.getStorageObjectKey());
        version.setFileChecksum(document.getFileChecksum()); version.setIndexStatus(0);
        knowledgeDocumentVersionService.save(version);
        // currentVersionNo 是已发布版本指针；异步任务成功前绝不能提前切换。
        KnowledgeDocument update = new KnowledgeDocument(); update.setId(document.getId()); update.setIndexStatus(1); update.setStatus(1);
        knowledgeDocumentService.updateById(update);
        return knowledgeDocumentIndexService.queueReindex(knowledgeDocumentService.getById(document.getId()), version, jobType);
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : hash) out.append(String.format("%02x", b));
        return out.toString();
    }
}
